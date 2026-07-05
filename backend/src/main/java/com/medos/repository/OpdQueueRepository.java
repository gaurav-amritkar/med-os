package com.medos.repository;

import com.medos.entity.OpdQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpdQueueRepository extends JpaRepository<OpdQueue, UUID> {
    List<OpdQueue> findByDoctorIdAndQueueStatus(UUID doctorId, OpdQueue.Status status);
    List<OpdQueue> findByDoctorIdOrderByCheckInAtAsc(UUID doctorId);
    Optional<OpdQueue> findFirstByDoctorIdAndQueueStatusOrderByCheckInAtAsc(UUID doctorId, OpdQueue.Status status);
}
