package com.fithub.patients.infrastructure.persistence;

import com.fithub.patients.domain.aggregate.BiometricHistory;
import com.fithub.patients.domain.repository.BiometricHistoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JpaBiometricHistoryRepository implements BiometricHistoryRepository {
    private final BiometricHistoryJpaRepository jpaRepository;

    public JpaBiometricHistoryRepository(BiometricHistoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(BiometricHistory history) {
        BiometricHistoryEntity entity = new BiometricHistoryEntity(
            history.getId(), null, history.getWeight(), history.getRecordedAt()
        );
        jpaRepository.save(entity);
    }

    @Override
    public List<BiometricHistory> findByPatientIdLast30Days(UUID patientId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return jpaRepository.findByPatientIdAfter(patientId, thirtyDaysAgo).stream()
            .map(e -> BiometricHistory.fromDatabase(e.getId(), e.getWeight(), e.getRecordedAt()))
            .collect(Collectors.toList());
    }

    @Override
    public List<BiometricHistory> findByPatientId(UUID patientId) {
        return jpaRepository.findByPatientIdOrderByRecordedAtDesc(patientId).stream()
            .map(e -> BiometricHistory.fromDatabase(e.getId(), e.getWeight(), e.getRecordedAt()))
            .collect(Collectors.toList());
    }
}
