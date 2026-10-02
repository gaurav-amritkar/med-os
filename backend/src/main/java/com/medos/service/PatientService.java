package com.medos.service;

import com.medos.dto.PageResponse;
import com.medos.dto.PatientDTO;
import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Consent;
import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.mapper.EntityDtoMapper;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.util.AuditLogger;
import com.medos.util.BlindIndexUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    /**
     * Most recent rows decrypted and matched for a name search.
     *
     * <p>A name search cannot be pushed into SQL, so it costs one decrypt per candidate row.
     * This bounds that cost. Beyond it, operators are warned rather than silently given a
     * partial answer.
     */
    static final int MAX_NAME_SCAN = 5000;

    private final PatientRepository patientRepository;
    private final ConsentRepository consentRepository;
    private final AuditLogger auditLogger;
    private final BlindIndexUtil blindIndexUtil;

    @Transactional
    public PatientDTO registerPatient(PatientRegistrationRequest req) {
        if (!Boolean.TRUE.equals(req.getDpdpConsent())) {
            throw new BusinessException("DPDP consent is required for patient registration");
        }
        if (req.getName() == null || req.getName().isBlank() || req.getName().trim().length() < 2) {
            throw new BusinessException("Patient name is required (min 2 chars)");
        }
        if (req.getAge() == null || req.getAge() < 0 || req.getAge() > 150) {
            throw new BusinessException("Invalid patient age (0-150)");
        }

        String trimmedName = req.getName().trim();

            // Create the tenant's data key before the entity reaches the persistence
            // context. Creating it from inside the converter would issue the key INSERT
            // while Hibernate is flushing, and reads must never write: an insert made
            // while decrypting a query result is discarded, leaving the tenant
            // permanently keyless.
            if (com.medos.security.TenantKeyHolder.isInitialised()) {
                com.medos.security.TenantKeyHolder.get().ensureDekExists();
            }
            // The name index needs its own key, created on the write path for the same
            // reason as the data key: deriving it during a read would issue a write
            // inside a query. Ordered after ensureDekExists() because the index key
            // is stored on the same tenant_keys row.
            if (com.medos.security.TenantKeyHolder.isInitialised()) {
                com.medos.security.TenantKeyHolder.get().ensureBlindIndexKeyExists();
            }
        Patient patient = Patient.builder()
                .uhid(generateUhid())
                .name(trimmedName)
                // Computed from the trimmed name, matching what search indexes.
                .nameIndex(blindIndexUtil.indexPatientName(trimmedName))
                .age(req.getAge())
                .gender(req.getGender())
                .phone(req.getPhone())
                .email(req.getEmail())
                .address(req.getAddress())
                .bloodGroup(req.getBloodGroup())
                .dpdpConsent(true)
                .dpdpConsentAt(LocalDateTime.now())
                .outstanding(java.math.BigDecimal.ZERO)
                .build();
        Patient saved = patientRepository.save(patient);

        Consent consent = Consent.builder()
                .patientId(saved.getId())
                .consentType("DPDP_DATA_PROCESSING")
                .granted(true)
                .grantedBy(saved.getName())
                .purpose(req.getConsentPurpose() != null ? req.getConsentPurpose() : "Treatment and billing")
                .build();
        consentRepository.save(consent);

        auditLogger.log("CREATE", "Patient", saved.getId().toString(),
                null, "UHID=" + saved.getUhid());

        return EntityDtoMapper.toDTO(saved);
    }

    /**
     * Lists patients, optionally filtered by a search term.
     *
     * <p>{@code name} is AES-GCM encrypted, so it cannot be matched with LIKE —
     * the previous {@code findByNameContainingIgnoreCase} returned nothing for
     * every query. Search therefore matches either the keyed blind index of the
     * whole normalised name, or the plaintext UHID, which is the identifier
     * reception staff actually read off a card.
     */
    public PageResponse<PatientDTO> listPatients(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Patient> result;
        if (search == null || search.isBlank()) {
            result = patientRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        } else {
            String term = search.trim();

            // A UHID is the common case and is searchable directly; a name fragment has to
            // be matched in the application, because SQL cannot see an encrypted column.
            if (looksLikeUhid(term)) {
                result = patientRepository
                        .findByUhidContainingIgnoreCaseOrderByCreatedAtDescIdDesc(term, pageable);
            } else {
                result = searchByNameFragment(term, page, size);
            }
        }
        return PageResponse.of(result.map(EntityDtoMapper::toDTO));
    }

    /**
     * Patients whose name contains the typed fragment, case-insensitively.
     *
     * <p>{@code name} is AES-GCM encrypted, so SQL cannot filter it, and the blind index is
     * an HMAC, which only ever matched the whole normalised name — so typing a first name
     * found nobody. Staff search on the fragment they actually remember, so the fragment is
     * matched here instead.
     *
     * <p>The scan is bounded by {@link #MAX_NAME_SCAN}. When the bound truncates the result
     * the caller is warned rather than being handed a quietly incomplete list: a search that
     * silently omits patients is worse than one that is visibly incomplete.
     *
     * <p>A row whose ciphertext cannot be decrypted fails the whole query, because the
     * converter runs while the result set is being mapped rather than on field access. That
     * is #118: rows written outside the encrypting path are unreadable, and the fix belongs
     * there rather than in a per-row guard here that could never run.
     */
    private Page<Patient> searchByNameFragment(String term, int page, int size) {
        String needle = BlindIndexUtil.normaliseName(term);
        if (needle.isEmpty()) {
            return Page.empty();
        }

        List<Patient> candidates = patientRepository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, MAX_NAME_SCAN))
                .getContent();
        long totalRows = patientRepository.count();
        if (totalRows > candidates.size()) {
            log.warn("Patient name search scanned only the {} most recent of {} rows; a match "
                    + "outside that window would be missed. Narrow the search or raise "
                    + "MAX_NAME_SCAN.", candidates.size(), totalRows);
        }

        List<Patient> matched = new ArrayList<>(candidates.size());
        for (Patient candidate : candidates) {
            if (BlindIndexUtil.normaliseName(candidate.getName()).contains(needle)) {
                matched.add(candidate);
            }
        }

        int total = matched.size();
        long offset = (long) page * size;
        PageRequest requested = PageRequest.of(page, size);
        if (offset >= total) {
            return new PageImpl<>(List.of(), requested, total);
        }
        int from = (int) offset;
        int to = Math.min(from + size, total);
        return new PageImpl<>(matched.subList(from, to), requested, total);
    }

    /** UHIDs are the UHID + 6 digits form, but staff also type the bare number. */
    private static boolean looksLikeUhid(String term) {
        String digits = term.toUpperCase(Locale.ROOT).startsWith("UHID")
                ? term.substring(4)
                : term;
        return digits.chars().allMatch(Character::isDigit) && !digits.isEmpty();
    }

    // Backward compatibility method
    public List<PatientDTO> listPatients(String search) {
        return listPatients(search, 0, Integer.MAX_VALUE).getContent();
    }

    public PatientDTO getPatient(UUID id) {
        return patientRepository.findById(id)
                .map(EntityDtoMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id.toString()));
    }

    public PatientDTO getByUhid(String uhid) {
        return patientRepository.findByUhid(uhid)
                .map(EntityDtoMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Patient UHID: " + uhid));
    }

    private String generateUhid() {
        return String.format("UHID%06d", patientRepository.getNextUhidSeq());
    }
}
