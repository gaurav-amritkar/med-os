package com.medos.repository;

import com.medos.entity.DischargeSummaryAddendum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DischargeSummaryAddendumRepository extends JpaRepository<DischargeSummaryAddendum, UUID> {
}