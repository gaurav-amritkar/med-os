package com.medos.repository;

import com.medos.entity.MedicineCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MedicineCatalogRepository extends JpaRepository<MedicineCatalog, UUID> {
    List<MedicineCatalog> findByNameContainingIgnoreCase(String name);
    List<MedicineCatalog> findByActiveTrue();
    List<MedicineCatalog> findByCategory(String category);

    @Query("SELECT m FROM MedicineCatalog m WHERE m.active = true AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.indications) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<MedicineCatalog> searchByKeyword(@Param("keyword") String keyword);
}
