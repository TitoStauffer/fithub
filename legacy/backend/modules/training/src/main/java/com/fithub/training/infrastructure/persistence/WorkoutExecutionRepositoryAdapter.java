package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.aggregate.WorkoutExecution;
import com.fithub.training.domain.repository.WorkoutExecutionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementando WorkoutExecutionRepository usando Spring Data JPA
 */
@Repository
public class WorkoutExecutionRepositoryAdapter implements WorkoutExecutionRepository {
    private final WorkoutExecutionJpaRepositorySpringData jpaRepository;

    public WorkoutExecutionRepositoryAdapter(WorkoutExecutionJpaRepositorySpringData jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(WorkoutExecution execution) {
        // Não implementado - usar Spring Data diretamente para entidade
        throw new UnsupportedOperationException("Use WorkoutExecutionJpaRepositorySpringData.save(entity) directly");
    }

    @Override
    public Optional<WorkoutExecution> findById(UUID id) {
        // Não implementado - usar Spring Data diretamente
        throw new UnsupportedOperationException("Use WorkoutExecutionJpaRepositorySpringData.findById(id) directly");
    }

    @Override
    public List<WorkoutExecution> findByPatientIdLast30Days(UUID patientId) {
        return List.of(); // TODO: Implementar conversão de entidade para aggregate
    }

    @Override
    public List<WorkoutExecution> findByPatientIdAndExerciseId(UUID patientId, UUID exerciseId) {
        return List.of(); // TODO: Implementar conversão de entidade para aggregate
    }
}
