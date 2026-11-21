package com.fithub.patients.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BiometricHistoryJpaRepository extends JpaRepository<BiometricHistoryEntity, UUID> {
    @Query("SELECT b FROM BiometricHistoryEntity b WHERE b.patientId = ?1 AND b.recordedAt >= ?2 ORDER BY b.recordedAt DESC")
    List<BiometricHistoryEntity> findByPatientIdAfter(UUID patientId, LocalDateTime since);

    List<BiometricHistoryEntity> findByPatientIdOrderByRecordedAtDesc(UUID patientId);
}
