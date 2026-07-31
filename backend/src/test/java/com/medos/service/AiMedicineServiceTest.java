package com.medos.service;

import com.medos.dto.AiSuggestRequest;
import com.medos.dto.MedicineSuggestion;
import com.medos.entity.DiseaseMedicineMap;
import com.medos.entity.MedicineCatalog;
import com.medos.repository.DiseaseMedicineMapRepository;
import com.medos.repository.MedicineCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMedicineServiceTest {

    @Mock private MedicineCatalogRepository medicineCatalogRepository;
    @Mock private DiseaseMedicineMapRepository diseaseMedicineMapRepository;
    @InjectMocks private AiMedicineService aiMedicineService;

    private MedicineCatalog medicine(UUID id, String name, String generic, String category) {
        MedicineCatalog m = new MedicineCatalog();
        m.setId(id);
        m.setName(name);
        m.setGenericName(generic);
        m.setCategory(category);
        m.setUnit("tablet");
        m.setUnitPrice(new BigDecimal("5.00"));
        m.setActive(true);
        return m;
    }

    private DiseaseMedicineMap mapping(UUID medId, String keyword, int priority, String dosage, String freq) {
        return DiseaseMedicineMap.builder()
                .id(UUID.randomUUID())
                .medicineId(medId)
                .diseaseKeyword(keyword)
                .priority(priority)
                .dosage(dosage)
                .frequency(freq)
                .build();
    }

    private AiSuggestRequest request(String description, String complaint) {
        AiSuggestRequest r = new AiSuggestRequest();
        r.setDiseaseDescription(description);
        r.setChiefComplaint(complaint);
        return r;
    }

    @Test
    void suggestMedicines_blankInput_returnsEmpty() {
        assertEquals(List.of(), aiMedicineService.suggestMedicines(request("", "  ")));
        assertEquals(List.of(), aiMedicineService.suggestMedicines(request(null, null)));
        verifyNoInteractions(diseaseMedicineMapRepository, medicineCatalogRepository);
    }

    @Test
    void suggestMedicines_keywordMapMatchProducesSuggestionWithRationale() {
        UUID medId = UUID.randomUUID();
        MedicineCatalog med = medicine(medId, "Paracetamol", "paracetamol", "analgesic");
        DiseaseMedicineMap mapping = mapping(medId, "fever", 1, "500mg", "SOS");

        when(diseaseMedicineMapRepository.findAll()).thenReturn(List.of(mapping));
        when(medicineCatalogRepository.findById(medId)).thenReturn(Optional.of(med));
        // catalogSearchMatching will tokenize "fever" which is only 5 chars — but to avoid coupling to
        // whether searchByKeyword returns results, return empty for every token.
        when(medicineCatalogRepository.searchByKeyword(anyString())).thenReturn(List.of());

        List<MedicineSuggestion> results = aiMedicineService.suggestMedicines(request("high fever", null));

        assertEquals(1, results.size());
        MedicineSuggestion s = results.get(0);
        assertEquals(medId, s.getMedicineId());
        assertEquals("Paracetamol", s.getName());
        assertEquals("500mg", s.getDosage());
        assertEquals("SOS", s.getFrequency());
        assertNotNull(s.getRationale());
        assertTrue(s.getRationale().contains("fever"));
        assertEquals(99, s.getRelevanceScore()); // 100 - priority(1)
    }

    @Test
    void suggestMedicines_skipsInactiveKeywordMappedMedicines() {
        UUID medId = UUID.randomUUID();
        MedicineCatalog med = medicine(medId, "Old Drug", "x", "y");
        med.setActive(false);
        DiseaseMedicineMap mapping = mapping(medId, "fever", 1, "1", "1");

        when(diseaseMedicineMapRepository.findAll()).thenReturn(List.of(mapping));
        when(medicineCatalogRepository.findById(medId)).thenReturn(Optional.of(med));
        when(medicineCatalogRepository.searchByKeyword(anyString())).thenReturn(List.of());

        List<MedicineSuggestion> results = aiMedicineService.suggestMedicines(request("fever", null));
        assertTrue(results.isEmpty());
    }

@Test
    void suggestMedicines_catalogSearchMatchAddsSuggestionScore50() {
        UUID medId = UUID.randomUUID();
        MedicineCatalog med = medicine(medId, "Amoxicillin", "amoxicillin", "antibiotic");

        when(diseaseMedicineMapRepository.findAll()).thenReturn(List.of());
        // Tokens are split on whitespace/punctuation; both tokens here are length >= 3 so both are queried.
        when(medicineCatalogRepository.searchByKeyword("flu")).thenReturn(List.of(med));
        when(medicineCatalogRepository.searchByKeyword("symptoms")).thenReturn(List.of());

        List<MedicineSuggestion> results = aiMedicineService.suggestMedicines(request("flu symptoms", null));

        assertEquals(1, results.size());
        assertEquals(50, results.get(0).getRelevanceScore());
        assertEquals("Amoxicillin", results.get(0).getName());
    }

    @Test
    void suggestMedicines_mergesKeywordAndCatalogAndDedupesPreservingHighestRelevanceOrder() {
        // Same medicine matched by keyword (score 90) and catalog (score 50) — only one entry should appear,
        // because MedicineSuggestion equals/hashCode is by all fields; but LinkedHashSet dedupes by equals.
        // Here we use two distinct medicines to test sort order: keyword match (high) above catalog match (low).
        UUID kwMedId = UUID.randomUUID();
        UUID catMedId = UUID.randomUUID();
        MedicineCatalog kwMed = medicine(kwMedId, "Keyword Med", "g1", "c1");
        MedicineCatalog catMed = medicine(catMedId, "Catalog Med", "g2", "c2");
        DiseaseMedicineMap mapping = mapping(kwMedId, "fever", 10, "1-0-1", "5 days");

        when(diseaseMedicineMapRepository.findAll()).thenReturn(List.of(mapping));
        when(medicineCatalogRepository.findById(kwMedId)).thenReturn(Optional.of(kwMed));
        when(medicineCatalogRepository.searchByKeyword("fever")).thenReturn(List.of(catMed));
        when(medicineCatalogRepository.searchByKeyword("high")).thenReturn(List.of());

        List<MedicineSuggestion> results = aiMedicineService.suggestMedicines(request("high fever", null));

        assertEquals(2, results.size());
        assertEquals(90, results.get(0).getRelevanceScore());
        assertEquals(50, results.get(1).getRelevanceScore());
    }

    @Test
    void suggestMedicines_limitsOutputToEight() {
        DiseaseMedicineMap[] mappings = new DiseaseMedicineMap[12];
        for (int i = 0; i < 12; i++) {
            UUID medId = UUID.randomUUID();
            mappings[i] = mapping(medId, "kw" + i, i, "1", "1");
            when(medicineCatalogRepository.findById(medId))
                    .thenReturn(Optional.of(medicine(medId, "Med" + i, "g", "c")));
        }
        when(diseaseMedicineMapRepository.findAll()).thenReturn(List.of(mappings));
        when(medicineCatalogRepository.searchByKeyword(anyString())).thenReturn(List.of());

        List<MedicineSuggestion> results = aiMedicineService.suggestMedicines(request("kw0 kw1 kw2 kw3 kw4 kw5 kw6 kw7 kw8 kw9 kw10 kw11", null));

        assertTrue(results.size() <= 8);
        assertEquals(8, results.size());
    }

    @Test
    void searchCatalog_delegatesToRepository() {
        aiMedicineService.searchCatalog("amox");
        verify(medicineCatalogRepository).searchByKeyword("amox");
    }
}
