package com.medos.repository;

import com.medos.entity.KeyRotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface KeyRotationRepository extends JpaRepository<KeyRotation, UUID> {
}
