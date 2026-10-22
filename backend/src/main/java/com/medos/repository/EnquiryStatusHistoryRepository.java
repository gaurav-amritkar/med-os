package com.medos.repository;

import com.medos.entity.EnquiryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnquiryStatusHistoryRepository
        extends JpaRepository<EnquiryStatusHistory, UUID> {

    List<EnquiryStatusHistory> findByEnquiryIdOrderByChangedAtDesc(UUID enquiryId);
}
