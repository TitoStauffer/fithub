package com.fithub.patients.domain.repository;

import com.fithub.patients.domain.aggregate.BiometricHistory;

import java.util.List;
import java.util.UUID;

public interface BiometricHistoryRepository {
    void save(BiometricHistory history);
    List<BiometricHistory> findByPatientIdLast30Days(UUID patientId);
    List<BiometricHistory> findByPatientId(UUID patientId);
}
