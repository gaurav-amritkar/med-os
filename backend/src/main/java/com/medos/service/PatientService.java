package com.medos.service;

import com.medos.dto.PageResponse;
import com.medos.dto.PatientConsentRequest;
import com.medos.dto.PatientDTO;
import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Admission;
import com.medos.entity.Consent;
import com.medos.entity.Encounter;
import com.medos.entity.Patient;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.mapper.EntityDtoMapper;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
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

    /**
     * The DPDP notice currently shown to patients.
     *
     * <p>Stamped on every consent event so the history can answer "what was this
     * person told?". A client may echo it, but may not invent an older one: the
     * server refuses any version it does not recognise rather than record a
     * claim nobody can verify.
     */
    public static final String CURRENT_DPDP_NOTICE_VERSION = "2026-10";

    private static final String DPDP_CONSENT_TYPE = "DPDP_DATA_PROCESSING";

    private final PatientRepository patientRepository;
    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogger auditLogger;
    private final BlindIndexUtil blindIndexUtil;

    @Transactional
    public PatientDTO registerPatient(PatientRegistrationRequest req) {
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
        // Consent is decoupled from registration (Wave 0, medworks-parity design):
        // registration no longer requires it. The stored flag mirrors what the caller
        // actually captured — an unconsented patient is registrable, and consent
        // itself is recorded as an auditable event only when it was granted. A
        // fabricated "declined" event would assert a conversation that never happened.
        boolean consentGranted = Boolean.TRUE.equals(req.getDpdpConsent());
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
                .dpdpConsent(consentGranted)
                .dpdpConsentAt(consentGranted ? LocalDateTime.now() : null)
                .outstanding(java.math.BigDecimal.ZERO)
                .build();
        Patient saved = patientRepository.save(patient);

        if (consentGranted) {
            Consent consent = Consent.builder()
                    .tenantId(saved.getTenantId())
                    .patientId(saved.getId())
                    .consentType(DPDP_CONSENT_TYPE)
                    .granted(true)
                    .grantedBy(currentStaffLabel())
                    .purpose(req.getConsentPurpose() != null ? req.getConsentPurpose() : "Treatment and billing")
                    .noticeVersion(CURRENT_DPDP_NOTICE_VERSION)
                    .build();
            consentRepository.save(consent);
        }

        auditLogger.log("CREATE", "Patient", saved.getId().toString(),
                null, "UHID=" + saved.getUhid());

        return EntityDtoMapper.toDTO(saved);
    }

    /**
     * Records a DPDP consent grant or withdrawal as a new event.
     *
     * <p>Registration no longer requires consent, so the front desk needs a way to
     * capture it later — and to record its withdrawal, which DPDP §6(4) makes a
     * right the data principal can exercise at any time. Both are appended as
     * rows: an earlier grant is never edited or deleted, because the history is
     * the evidence of what the patient agreed to and when.
     *
     * <p>The patient row's {@code dpdp_consent} flag is a cache of the latest
     * event, kept so list and banner reads do not need a second query. The event
     * table remains the authority.
     *
     * <p>Cross-tenant access is refused by the read-side tenant predicate, not by
     * an explicit check: {@code findById} on another hospital's patient returns
     * empty and this becomes a 404, the same answer as a patient that does not
     * exist.
     */
    @Transactional
    public PatientDTO recordConsent(UUID patientId, PatientConsentRequest request) {
        if (request == null || request.getGranted() == null) {
            throw new BusinessException("granted is required");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        boolean granted = request.getGranted();
        boolean previous = Boolean.TRUE.equals(patient.getDpdpConsent());
        String noticeVersion = resolveNoticeVersion(request.getNoticeVersion());
        LocalDateTime capturedAt = LocalDateTime.now();

        Consent consent = Consent.builder()
                .tenantId(patient.getTenantId())
                .patientId(patient.getId())
                .consentType(DPDP_CONSENT_TYPE)
                .granted(granted)
                .grantedAt(capturedAt)
                .grantedBy(currentStaffLabel())
                .purpose(request.getPurpose() != null && !request.getPurpose().isBlank()
                        ? request.getPurpose().trim()
                        : "Treatment and billing")
                .noticeVersion(noticeVersion)
                .build();
        consentRepository.save(consent);

        patient.setDpdpConsent(granted);
        patient.setDpdpConsentAt(capturedAt);
        patientRepository.save(patient);

        auditLogger.log(granted ? "CONSENT_GRANTED" : "CONSENT_WITHDRAWN", "Patient",
                patient.getId().toString(),
                "dpdpConsent=" + previous,
                "dpdpConsent=" + granted + ";noticeVersion=" + noticeVersion);

        return EntityDtoMapper.toDTO(patient);
    }

    private String resolveNoticeVersion(String requested) {
        if (requested == null || requested.isBlank()) {
            return CURRENT_DPDP_NOTICE_VERSION;
        }
        String trimmed = requested.trim();
        if (!CURRENT_DPDP_NOTICE_VERSION.equals(trimmed)) {
            throw new BusinessException("Unknown consent notice version: " + trimmed
                    + ". The current notice is " + CURRENT_DPDP_NOTICE_VERSION);
        }
        return trimmed;
    }

    /**
     * Username of the signed-in staff member who captured the consent.
     *
     * <p>The old registration path wrote the <em>patient's</em> name here, which
     * made the consent record claim the patient recorded their own consent. The
     * JWT principal is the user id, so it is resolved to a username; bootstrap and
     * test paths with no authentication are labelled {@code system} rather than
     * inventing a person.
     */
    private String currentStaffLabel() {
        UUID userId = currentUserProvider == null ? null : currentUserProvider.getCurrentUserId();
        if (userId == null) {
            return "system";
        }
        return userRepository == null ? userId.toString()
                : userRepository.findById(userId).map(User::getUsername).orElse(userId.toString());
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
    /**
     * The Patients Hub counter rail (Wave 1, medworks-parity row 3):
     * Total / New Today / Active OPD / Inpatient (IPD) / Discharged — one
     * group of count queries answering the whole rail at once, the same
     * pattern the Enquiry Desk summary established.
     */
    public record HubSummary(long total, long newToday, long activeOpd, long ipd, long discharged) {}

    public HubSummary hubSummary() {
        LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        return new HubSummary(
                patientRepository.count(),
                patientRepository.countByCreatedAtAfter(startOfDay),
                patientRepository.countActiveOpdPatients(),
                patientRepository.countIpdPatients(),
                patientRepository.countDischargedPatients());
    }

    /**
     * The board's cohort radios: All / Active / Admitted (IPD) / Discharged.
     * Emergency has no per-patient marker in this schema yet, so it is not a
     * radio; inventing one out of a vitals string would be a fake filter.
     */
    private static final java.util.Set<String> HUB_TYPES =
            java.util.Set.of("active", "ipd", "active_opd", "discharged");

    /**
     * Patients Hub list with cohort + blood-group filters.
     *
     * <p>Filters route through a Specification so the row selection happens in
     * SQL (EXISTS on admissions/encounters) instead of per-row queries.
     * {@code search} composes inside that path by UHID only: {@code name} is
     * AES-GCM ciphertext SQL cannot match. A name fragment together with a
     * filter is refused with a stated error rather than silently ignoring the
     * filter — the failure mode the silent-omission issue (#118 family) exists
     * to prevent. Name search alone keeps the decrypt-scan path unchanged.
     */
    public PageResponse<PatientDTO> listPatients(String search, String type, String bloodGroup, int page, int size) {
        String blood = bloodGroup == null || bloodGroup.isBlank() ? null : bloodGroup.trim().toUpperCase(Locale.ROOT);
        String cohort = type == null || type.isBlank() || "all".equals(type) ? null : type;
        if (cohort != null && !HUB_TYPES.contains(cohort)) {
            throw new BusinessException("Unknown patient type filter: " + cohort);
        }
        if (cohort == null && blood == null) {
            return listPatients(search, page, size);
        }
        String term = search == null ? null : search.trim();
        if (term != null && !term.isBlank() && !looksLikeUhid(term)) {
            throw new BusinessException(
                    "Name search cannot be combined with the type or blood-group filters; search by UHID instead");
        }
        var spec = hubSpecification(term, cohort, blood);
        var result = patientRepository.findAll(spec,
                PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result.map(EntityDtoMapper::toDTO));
    }

    private org.springframework.data.jpa.domain.Specification<Patient> hubSpecification(
            String uhidTerm, String cohort, String blood) {
        return (root, query, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (uhidTerm != null && !uhidTerm.isBlank()) {
                preds.add(cb.like(cb.upper(root.get("uhid")),
                        "%" + uhidTerm.toUpperCase(Locale.ROOT) + "%"));
            }
            if (blood != null) {
                preds.add(cb.equal(root.get("bloodGroup"), blood));
            }
            if (cohort != null) {
                preds.add(cohortPredicate(root, query, cb, cohort));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** EXISTS subqueries in SQL, not per-row repository calls. */
    private jakarta.persistence.criteria.Predicate cohortPredicate(
            jakarta.persistence.criteria.Root<Patient> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String cohort) {
        return switch (cohort) {
            case "ipd" -> cb.exists(admissionExists(root, query, cb, Admission.Status.admitted));
            case "discharged" -> cb.exists(admissionExists(root, query, cb, Admission.Status.discharged));
            case "active_opd" -> {
                var sub = query.subquery(Long.class);
                var e = sub.from(Encounter.class);
                sub.select(cb.literal(1L));
                sub.where(cb.equal(e.get("patientId"), root.get("id")),
                        cb.equal(e.get("status"), Encounter.Status.open));
                yield cb.exists(sub);
            }
            // Active = anywhere in today's care: admitted OR an open encounter.
            default -> cb.or(
                    cb.exists(admissionExists(root, query, cb, Admission.Status.admitted)),
                    cb.exists(openEncounterExists(root, query, cb)));
        };
    }

    private jakarta.persistence.criteria.Subquery<Long> admissionExists(
            jakarta.persistence.criteria.Root<Patient> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Admission.Status status) {
        var sub = query.subquery(Long.class);
        var a = sub.from(Admission.class);
        sub.select(cb.literal(1L));
        sub.where(cb.equal(a.get("patientId"), root.get("id")),
                cb.equal(a.get("status"), status));
        return sub;
    }

    private jakarta.persistence.criteria.Subquery<Long> openEncounterExists(
            jakarta.persistence.criteria.Root<Patient> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        var sub = query.subquery(Long.class);
        var e = sub.from(Encounter.class);
        sub.select(cb.literal(1L));
        sub.where(cb.equal(e.get("patientId"), root.get("id")),
                cb.equal(e.get("status"), Encounter.Status.open));
        return sub;
    }

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
