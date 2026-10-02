package com.medos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.medos.entity.Patient;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.security.FakeTenantKeyStore;
import com.medos.util.AuditLogger;
import com.medos.util.BlindIndexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Pageable;

/**
 * Name search must find a patient when the typed text appears anywhere in their name,
 * case-insensitively.
 *
 * <p>The blind index is an HMAC, which is exact-match by construction, so "Gaurav" could
 * never find "Gaurav Amritkar". Staff type the name they know, which is usually part of
 * it, so this was a dead end for the most common query in the system.
 *
 * <p>{@code name} is AES-GCM encrypted, so SQL cannot filter it. These tests pin the
 * behaviour that replaces that: decrypt a bounded set of candidates and match in the
 * application.
 */
@ExtendWith(MockitoExtension.class)
class PatientNameContainsSearchTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    /** 32 bytes, Base64: the holder rejects anything else at construction. */
    private static final String TEST_KEK = java.util.Base64.getEncoder()
            .encodeToString("kek-kek-kek-kek-kek-kek-kek-kek!".getBytes());

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private PatientService service;

    private Patient patient;

    @BeforeEach
    void setUp() {
        // A real index and a real holder, not mocks, so the search routing under test is
        // the production one rather than a stub that agrees with whatever I wrote.
        com.medos.security.TenantContext.setTenantId(TENANT_A);
        com.medos.security.TenantKeyHolder.reset();
        com.medos.security.TenantKeyHolder.setInstance(
                new com.medos.security.TenantKeyHolder(TEST_KEK, new FakeTenantKeyStore()));
        ReflectionTestUtils.setField(service, "blindIndexUtil", new BlindIndexUtil());

        patient = Patient.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_A)
                .uhid("UHID000001")
                .name("Gaurav Amritkar")
                .age(35)
                .gender("male")
                .phone("8380850535")
                .email("g@example.test")
                .address("Baner")
                .bloodGroup("A+")
                .build();
    }

    @AfterEach
    void tearDown() {
        com.medos.security.TenantKeyHolder.reset();
        com.medos.security.TenantContext.clear();
    }

    private void givenCandidates(List<Patient> rows) {
        when(patientRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(rows));
        // Only the name path consults the total, so this must not be a strict stub.
        lenient().when(patientRepository.count()).thenReturn((long) rows.size());
    }

    @Nested
    @DisplayName("a typed fragment of the name finds the patient")
    class Fragments {

        @Test
        @DisplayName("a first name alone matches the full name")
        void firstNameMatches() {
            givenCandidates(List.of(patient));

            var page = service.listPatients("Gaurav", 0, 20);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("Gaurav Amritkar");
        }

        @Test
        @DisplayName("a surname alone matches the full name")
        void surnameMatches() {
            givenCandidates(List.of(patient));

            assertThat(service.listPatients("Amritkar", 0, 20).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("a fragment from the middle of a name part matches")
        void middleFragmentMatches() {
            givenCandidates(List.of(patient));

            // "urav" sits inside "Gaurav" and matches no whole word, so this only passes
            // if the match is a genuine substring test rather than a prefix or word test.
            assertThat(service.listPatients("urav", 0, 20).getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("matching ignores case and stray whitespace")
    class Normalisation {

        @Test
        @DisplayName("an uppercase query matches a lowercase name")
        void queryIsCaseInsensitive() {
            givenCandidates(List.of(patient));

            assertThat(service.listPatients("GAURAV", 0, 20).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("extra internal whitespace in the query still matches")
        void queryWhitespaceIsCollapsed() {
            givenCandidates(List.of(patient));

            assertThat(service.listPatients("  Gaurav   Amritkar  ", 0, 20).getContent())
                    .hasSize(1);
        }

        @Test
        @DisplayName("the full name still matches exactly")
        void fullNameMatches() {
            givenCandidates(List.of(patient));

            assertThat(service.listPatients("Gaurav Amritkar", 0, 20).getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("non-matching text returns nothing")
    class NonMatching {

        @Test
        @DisplayName("an unrelated name returns no rows")
        void unrelatedNameReturnsEmpty() {
            givenCandidates(List.of(patient));

            assertThat(service.listPatients("Somebody Else", 0, 20).getContent()).isEmpty();
        }

        @Test
        @DisplayName("only the matching rows are returned from a mixed set")
        void filtersToMatchesOnly() {
            Patient other = Patient.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT_A).uhid("UHID000002")
                    .name("Anita Joshi").build();
            givenCandidates(List.of(patient, other));

            var page = service.listPatients("Gaurav", 0, 20);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getName()).isEqualTo("Gaurav Amritkar");
        }
    }

    @Nested
    @DisplayName("a UHID query keeps using the indexed lookup")
    class UhidPath {

        @Test
        @DisplayName("a UHID search does not fall back to the name scan")
        void uhidSearchUsesTheIndexedQuery() {
            when(patientRepository
                    .findByUhidContainingIgnoreCaseOrderByCreatedAtDescIdDesc(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(patient)));

            var page = service.listPatients("UHID0000", 0, 20);

            assertThat(page.getContent()).hasSize(1);
            verify(patientRepository, never())
                    .findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class));
        }
    }

}
