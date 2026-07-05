package com.medos.repository;

import com.medos.entity.DiseaseMedicineMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiseaseMedicineMapRepository extends JpaRepository<DiseaseMedicineMap, UUID> {
    Optional<DiseaseMedicineMap> findByDiseaseKeywordIgnoreCase(String keyword);
    List<DiseaseMedicineMap> findByDiseaseKeywordContainingIgnoreCase(String keyword);
}
