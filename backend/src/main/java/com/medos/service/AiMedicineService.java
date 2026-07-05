package com.medos.service;

import com.medos.dto.AiSuggestRequest;
import com.medos.dto.MedicineSuggestion;
import com.medos.entity.DiseaseMedicineMap;
import com.medos.entity.MedicineCatalog;
import com.medos.repository.DiseaseMedicineMapRepository;
import com.medos.repository.MedicineCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMedicineService {

    private final MedicineCatalogRepository medicineCatalogRepository;
    private final DiseaseMedicineMapRepository diseaseMedicineMapRepository;

    @Value("${medos.ai.enabled:false}")
    private boolean aiEnabled;

    public List<MedicineSuggestion> suggestMedicines(AiSuggestRequest request) {
        String text = (request.getDiseaseDescription() != null ? request.getDiseaseDescription() : "")
                + " " + (request.getChiefComplaint() != null ? request.getChiefComplaint() : "");
        text = text.toLowerCase().trim();

        if (text.isBlank()) {
            return Collections.emptyList();
        }

        Set<MedicineSuggestion> suggestions = new LinkedHashSet<>();
        suggestions.addAll(keywordMapMatching(text));
        suggestions.addAll(catalogSearchMatching(text));

        List<MedicineSuggestion> ordered = new ArrayList<>(suggestions);
        ordered.sort(Comparator.comparing(MedicineSuggestion::getRelevanceScore, Comparator.nullsLast(Comparator.reverseOrder())));

        return ordered.stream().limit(8).collect(Collectors.toList());
    }

    @Cacheable(value = "medicineCatalog", key = "#keyword")
    public List<MedicineCatalog> searchCatalog(String keyword) {
        return medicineCatalogRepository.searchByKeyword(keyword);
    }

    private List<MedicineSuggestion> keywordMapMatching(String text) {
        List<DiseaseMedicineMap> mappings = diseaseMedicineMapRepository.findAll().stream()
                .filter(m -> text.contains(m.getDiseaseKeyword().toLowerCase()))
                .sorted(Comparator.comparing(DiseaseMedicineMap::getPriority))
                .collect(Collectors.toList());

        List<MedicineSuggestion> out = new ArrayList<>();
        for (DiseaseMedicineMap m : mappings) {
            MedicineCatalog med = medicineCatalogRepository.findById(m.getMedicineId()).orElse(null);
            if (med == null || !med.getActive()) continue;
            out.add(MedicineSuggestion.builder()
                    .medicineId(med.getId())
                    .name(med.getName())
                    .genericName(med.getGenericName())
                    .category(med.getCategory())
                    .unit(med.getUnit())
                    .unitPrice(med.getUnitPrice())
                    .dosage(m.getDosage())
                    .frequency(m.getFrequency())
                    .rationale("Matched on keyword: " + m.getDiseaseKeyword())
                    .relevanceScore(100 - (m.getPriority() != null ? m.getPriority() : 0))
                    .build());
        }
        return out;
    }

    private List<MedicineSuggestion> catalogSearchMatching(String text) {
        String[] tokens = text.split("[\\s,;.]+");
        Set<MedicineSuggestion> matches = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() < 3) continue;
            List<MedicineCatalog> results = searchCatalog(token);
            for (MedicineCatalog med : results) {
                matches.add(MedicineSuggestion.builder()
                        .medicineId(med.getId())
                        .name(med.getName())
                        .genericName(med.getGenericName())
                        .category(med.getCategory())
                        .unit(med.getUnit())
                        .unitPrice(med.getUnitPrice())
                        .rationale("Catalog match: " + token)
                        .relevanceScore(50)
                        .build());
            }
        }
        return new ArrayList<>(matches);
    }
}
