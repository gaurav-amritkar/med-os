package com.medos.modules.pharmacy.service;

import com.medos.dto.PageResponse;
import com.medos.entity.MedicineCatalog;
import com.medos.repository.MedicineCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicineCatalogService {

    private final MedicineCatalogRepository medicineCatalogRepository;

    public PageResponse<MedicineCatalog> listAllMedicines(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MedicineCatalog> result = medicineCatalogRepository.findByActiveTrue(pageable);
        return PageResponse.of(result);
    }

    public List<MedicineCatalog> listAllMedicines() {
        return medicineCatalogRepository.findByActiveTrue();
    }

    public PageResponse<MedicineCatalog> searchMedicines(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MedicineCatalog> result = medicineCatalogRepository.searchByKeyword(keyword, pageable);
        return PageResponse.of(result);
    }

    public PageResponse<MedicineCatalog> searchMedicinesByName(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MedicineCatalog> result = medicineCatalogRepository.findByNameContainingIgnoreCase(name, pageable);
        return PageResponse.of(result);
    }
}