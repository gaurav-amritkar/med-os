package com.medos.modules.enquiry.service;

import com.medos.dto.CreateEnquiryRequest;
import com.medos.dto.EnquiryDTO;
import com.medos.dto.PageResponse;
import com.medos.dto.UpdateEnquiryStatusRequest;
import com.medos.entity.Enquiry;
import com.medos.entity.EnquiryStatusHistory;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.EnquiryRepository;
import com.medos.repository.EnquiryStatusHistoryRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.security.TenantContext;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The desk's operations: log an enquiry, list the board with its filters, move
 * an enquiry across its lifecycle. Every transition appends to
 * {@code enquiry_status_history} (never edits it), so the board answers "what
 * happened to this one" from records; every desk action is audited.
 *
 * <p>Numbering: {@code enquiry_no} is a per-tenant serial, computed as the
 * tenant's current max + 1. Two concurrent logs can ask for the same number —
 * V7's unique index {@code uq_enquiries_tenant_no} makes exactly one win, and
 * the loser retries, claiming again. The desk is a low-contention surface;
 * three attempts and the check constraint is the whole race policy.
 */
@Service
@RequiredArgsConstructor
public class EnquiryService {

    private static final List<String> STATUS_VALUES =
            List.of("new_enquiry", "follow_up", "converted", "cancelled");

    private static final DateTimeFormatter NOTE_STAMP =
            DateTimeFormatter.ofPattern("d-MMM", Locale.ENGLISH);

    private final EnquiryRepository enquiryRepository;
    private final EnquiryStatusHistoryRepository historyRepository;
    private final AuditLogger auditLogger;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate txTemplate;

    /**
     * Log an enquiry. Fact and trail land in ONE transaction (the desk must
     * never show an enquiry without its origin row in history), and each
     * numbering attempt is its own transaction so the unique-index conflict
     * from a concurrent sibling rolls back only that attempt, not the caller's
     * work.
     */
    public EnquiryDTO create(CreateEnquiryRequest request) {
        UUID tenantId = tenant();
        UUID actingUser = currentUserProvider.getCurrentUserId();
        Enquiry.EnquiryType type = parseTypeOrThrow(request.type());

        RuntimeException conflict = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return txTemplate.execute(status -> {
                    Enquiry enquiry = Enquiry.builder()
                            .tenantId(tenantId)
                            .contactName(request.contactName().trim())
                            .contactPhone(trimmed(request.contactPhone()))
                            .contactEmail(trimmed(request.contactEmail()))
                            .enquiryType(type)
                            .department(trimmed(request.department()))
                            .reason(request.reason().trim())
                            .notes(trimmed(request.notes()))
                            .status(Enquiry.EnquiryStatus.new_enquiry)
                            .createdBy(actingUser)
                            .build();
                    Integer max = enquiryRepository.findMaxEnquiryNo();
                    enquiry.setEnquiryNo((max == null ? 0 : max) + 1);
                    Enquiry saved = enquiryRepository.saveAndFlush(enquiry);

                    historyRepository.save(EnquiryStatusHistory.builder()
                            .tenantId(tenantId)
                            .enquiryId(saved.getId())
                            .fromStatus(null)
                            .toStatus(saved.getStatus())
                            .note(trimmed(request.notes()))
                            .changedBy(actingUser)
                            .build());
                    return EnquiryDTO.from(saved);
                });
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                conflict = e;
            }
        }
        throw conflict != null ? conflict
                : new IllegalStateException("could not assign an enquiry number");
    }

    public PageResponse<EnquiryDTO> list(String status, String type, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        validateStatusOrThrow(status);
        Enquiry.EnquiryType enquiryType = parseTypeOrThrow(type);
        // No tenant predicate needed in the query: the read inspector scopes the
        // SELECT root to the acting tenant. The filters are the page's selects.
        org.springframework.data.jpa.domain.Specification<Enquiry> spec =
                (root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (status != null && !status.isBlank()) {
                        predicates.add(cb.equal(root.get("status"), Enquiry.EnquiryStatus
                                .valueOf(status.trim().toLowerCase(Locale.ROOT))));
                    }
                    if (enquiryType != null) {
                        predicates.add(cb.equal(root.get("enquiryType"), enquiryType));
                    }
                    return predicates.isEmpty()
                            ? cb.conjunction()
                            : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
        Page<Enquiry> result = enquiryRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(EnquiryDTO::from));
    }

    /** The counter rail above the board, per the MedWorks grammar. */
    public DeskSummary summary() {
        long total = enquiryRepository.countAllEnquiries();
        var byStatus = new java.util.EnumMap<Enquiry.EnquiryStatus, Long>(
                Enquiry.EnquiryStatus.class);
        for (Object[] row : enquiryRepository.countByStatus()) {
            byStatus.merge((Enquiry.EnquiryStatus) row[0], (Long) row[1], Long::sum);
        }
        return new DeskSummary(total,
                byStatus.getOrDefault(Enquiry.EnquiryStatus.new_enquiry, 0L),
                byStatus.getOrDefault(Enquiry.EnquiryStatus.follow_up, 0L),
                byStatus.getOrDefault(Enquiry.EnquiryStatus.converted, 0L),
                byStatus.getOrDefault(Enquiry.EnquiryStatus.cancelled, 0L));
    }

    public record DeskSummary(long total, long newEnquiries, long followUp,
                              long converted, long cancelled) {
    }

    public List<EnquiryStatusHistory> history(UUID enquiryId) {
        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", enquiryId.toString()));
        return historyRepository.findByEnquiryIdOrderByChangedAtDesc(enquiry.getId());
    }

    @Transactional
    public EnquiryDTO updateStatus(UUID id, UpdateEnquiryStatusRequest request) {
        UUID tenantId = tenant();
        Enquiry.EnquiryStatus to = Enquiry.EnquiryStatus.valueOf(
                request.status().trim().toLowerCase(Locale.ROOT));
        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", id.toString()));
        Enquiry.EnquiryStatus from = enquiry.getStatus();

        // Lifecycle rules: a done enquiry is done. Converted/cancelled enquiries
        // do not flip status silently — the trail is the record of what was promised.
        if (from == Enquiry.EnquiryStatus.converted || from == Enquiry.EnquiryStatus.cancelled) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This enquiry is already " + from + " and cannot change status.");
        }

        enquiry.setStatus(to);
        if (to == Enquiry.EnquiryStatus.follow_up && request.note() != null) {
            enquiry.setNotes(mergeNote(enquiry.getNotes(), request.note()));
        }
        Enquiry saved = enquiryRepository.save(enquiry);
        historyRepository.save(EnquiryStatusHistory.builder()
                .tenantId(tenantId)
                .enquiryId(saved.getId())
                .fromStatus(from)
                .toStatus(to)
                .note(trimmed(request.note()))
                .changedBy(currentUserProvider.getCurrentUserId())
                .build());
        auditLogger.log("ENQUIRY_STATUS_CHANGED", "Enquiry", saved.getId().toString(),
                String.valueOf(from), String.valueOf(to));
        return EnquiryDTO.from(saved);
    }

    private String mergeNote(String existing, String addition) {
        String add = trimmed(addition);
        if (add == null) {
            return existing;
        }
        return (existing == null ? "" : existing + " | ")
                + NOTE_STAMP.format(LocalDate.now()) + ": " + add;
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private Enquiry.EnquiryType parseTypeOrThrow(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return Enquiry.EnquiryType.valueOf(type.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown enquiry type filter: " + type);
        }
    }

    private void validateStatusOrThrow(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        String candidate = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_VALUES.contains(candidate)) {
            throw new BusinessException("Unknown enquiry status filter: " + status);
        }
    }

    private UUID tenant() {
        return TenantContext.getTenantId().orElseThrow(
                () -> new BusinessException("No organisation is selected for this session"));
    }
}
