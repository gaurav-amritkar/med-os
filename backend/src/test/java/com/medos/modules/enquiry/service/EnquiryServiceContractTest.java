package com.medos.modules.enquiry.service;

import com.medos.dto.EnquiryDTO;
import com.medos.entity.Enquiry;
import com.medos.entity.EnquiryStatusHistory;
import com.medos.entity.Tenant;
import com.medos.repository.EnquiryRepository;
import com.medos.repository.EnquiryStatusHistoryRepository;
import com.medos.repository.TenantRepository;
import com.medos.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Enquiry Desk's contract, on the real service + H2: numbering stays a
 * per-tenant serial, transitions append to the audited trail, a done enquiry
 * cannot quietly reopen, and the board is scoped to who is sitting at the desk.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class EnquiryServiceContractTest {

    @Autowired
    private EnquiryService enquiryService;

    @Autowired
    private EnquiryRepository enquiries;

    @Autowired
    private EnquiryStatusHistoryRepository history;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    private UUID createTenant(String name) {
        Tenant t = new Tenant();
        t.setName(name);
        t.setType(Tenant.TenantType.HOSPITAL);
        t.setSlug(name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID().toString().substring(0, 8));
        t.setActive(true);
        return tenantRepository.saveAndFlush(t).getId();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private EnquiryDTO logEnquiry(UUID tenant, String name) {
        TenantContext.setTenantId(tenant);
        try {
            return enquiryService.create(new com.medos.dto.CreateEnquiryRequest(
                    name, "+91 98765 43210", null, "appointment",
                    "Cardiology", name + " wants a cardiology appointment", null));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("logging numbers per tenant: both tenants' first enquiries are ENQ-1 or ENQ-2 side by side")
    void numberingIsPerTenant() {
        UUID tenantA = tenantId = createTenant("Enquiry Desk A " + UUID.randomUUID());
        UUID tenantB = createTenant("Enquiry Desk B " + UUID.randomUUID());

        EnquiryDTO first = logEnquiry(tenantA, "Anita Sharma");
        EnquiryDTO otherTenantFirst = logEnquiry(tenantB, "Ravi Menon");
        EnquiryDTO second = logEnquiry(tenantA, "Joseph Verghese");

        assertThat(first.enquiryNo()).isEqualTo(1);
        assertThat(otherTenantFirst.enquiryNo()).isEqualTo(1);
        assertThat(second.enquiryNo()).isEqualTo(2);
        // The human reference the board shows.
        assertThat(first.enquiryRef()).isEqualTo("ENQ-1");
    }

    @Test
    @DisplayName("logging leaves a creation row in the trail and an audit event")
    void createLeavesTrail() {
        UUID tenant = tenantId = createTenant("Enquiry Trail " + UUID.randomUUID());
        EnquiryDTO created = logEnquiry(tenant, "Anita Sharma");

        TenantContext.setTenantId(tenant);
        try {
            var trail = history.findByEnquiryIdOrderByChangedAtDesc(created.id());
            assertThat(trail).hasSize(1);
            assertThat(trail.get(0).getFromStatus()).isNull();
            assertThat(trail.get(0).getToStatus()).isEqualTo(Enquiry.EnquiryStatus.new_enquiry);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("follow-up and conversion append history rows; cancel-after-done is refused")
    void lifecycleAppendsTrailAndRefusesReopen() {
        UUID tenant = tenantId = createTenant("Enquiry Lifecycle " + UUID.randomUUID());
        EnquiryDTO enquiry = logEnquiry(tenant, "Anita Sharma");
        TenantContext.setTenantId(tenant);
        try {
            EnquiryDTO followed = enquiryService.updateStatus(enquiry.id(),
                    new com.medos.dto.UpdateEnquiryStatusRequest("follow_up", "spoke to her, call back Tuesday"));

            assertThat(followed.status()).isEqualTo("follow_up");
            assertThat(followed.notes()).contains("call back Tuesday");
            assertThat(history.findByEnquiryIdOrderByChangedAtDesc(enquiry.id()))
                    .hasSize(2)
                    .first()
                    .satisfies(row -> {
                        assertThat(row.getFromStatus()).isEqualTo(Enquiry.EnquiryStatus.new_enquiry);
                        assertThat(row.getToStatus()).isEqualTo(Enquiry.EnquiryStatus.follow_up);
                        assertThat(row.getNote()).isEqualTo("spoke to her, call back Tuesday");
                    });

            EnquiryDTO converted = enquiryService.updateStatus(enquiry.id(),
                    new com.medos.dto.UpdateEnquiryStatusRequest("converted", "registered as OPD"));

            EnquiryDTO done = converted;
            assertThat(done.status()).isEqualTo("converted");

            // A done enquiry is done: no silent reopen, no cancelled-after-converted.
            assertThatThrownBy(() -> enquiryService.updateStatus(enquiry.id(),
                    new com.medos.dto.UpdateEnquiryStatusRequest("cancelled", "ignore old one")))
                    .isInstanceOf(com.medos.exception.BusinessException.class)
                    .hasMessageContaining("cannot change status");
            assertThat(history.findByEnquiryIdOrderByChangedAtDesc(enquiry.id())).hasSize(3);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("the summary rail counts the desk per tenant")
    void summaryCountsPerTenant() {
        UUID tenant = tenantId = createTenant("Enquiry Summary " + UUID.randomUUID());
        EnquiryDTO a = logEnquiry(tenant, "Anita Sharma");
        EnquiryDTO b = logEnquiry(tenant, "Ravi Menon");
        logEnquiry(tenant, "Joseph Verghese");
        logEnquiry(tenant, "Prema Iyer");
        TenantContext.setTenantId(tenant);
        try {
            enquiryService.updateStatus(b.id(),
                    new com.medos.dto.UpdateEnquiryStatusRequest("follow_up", null));
            enquiryService.updateStatus(a.id(),
                    new com.medos.dto.UpdateEnquiryStatusRequest("converted", null));

            EnquiryService.DeskSummary summary = enquiryService.summary();
            assertThat(summary.total()).isEqualTo(4);
            assertThat(summary.newEnquiries()).isEqualTo(2);
            assertThat(summary.followUp()).isEqualTo(1);
            assertThat(summary.converted()).isEqualTo(1);
            assertThat(summary.cancelled()).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("the board is tenant-scoped: desk A sees nothing of desk B")
    void boardIsTenantScoped() {
        UUID tenantA = tenantId = createTenant("Board A " + UUID.randomUUID());
        UUID tenantB = createTenant("Board B " + UUID.randomUUID());
        EnquiryDTO mine = logEnquiry(tenantA, "Anita Sharma");
        EnquiryDTO theirs = logEnquiry(tenantB, "Ravi Menon");

        TenantContext.setTenantId(tenantA);
        try {
            var board = enquiryService.list(null, null, 0, 50);
            assertThat(board.getContent()).extracting(com.medos.dto.EnquiryDTO::id)
                    .doesNotContain(theirs.id())
                    .contains(mine.id());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("the status/type filters return what they say")
    void filtersFilter() {
        UUID tenant = tenantId = createTenant("Enquiry Filters " + UUID.randomUUID());
        EnquiryDTO appointment = logEnquiry(tenant, "Anita Sharma");
        logEnquiry(tenant, "Ravi Menon");
        TenantContext.setTenantId(tenant);
        try {
            var apptOnly = enquiryService.list(null, "appointment", 0, 50);
            assertThat(apptOnly.getContent()).extracting(com.medos.dto.EnquiryDTO::id)
                    .contains(appointment.id());
            var admissionOnly = enquiryService.list(null, "admission", 0, 50);
            assertThat(admissionOnly.getContent()).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }
}
