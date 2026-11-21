package com.fithub.training.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository para WorkoutExecutionEntity
 */
@Repository
public interface WorkoutExecutionJpaRepositorySpringData extends JpaRepository<WorkoutExecutionEntity, UUID> {

    /**
     * Busca todas as execuções de um paciente
     */
    List<WorkoutExecutionEntity> findByPatientId(UUID patientId);

    /**
     * Busca execuções de um paciente ordenadas por data descendente
     */
    List<WorkoutExecutionEntity> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    /**
     * Busca execuções de treino de um paciente nos últimos 30 dias
     */
    @Query("SELECT w FROM WorkoutExecutionEntity w " +
           "WHERE w.patientId = :patientId " +
           "AND w.recordedAt >= :thirtyDaysAgo " +
           "ORDER BY w.recordedAt DESC")
    List<WorkoutExecutionEntity> findByPatientIdLast30Days(
        @Param("patientId") UUID patientId,
        @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo
    );

    /**
     * Busca execuções de um exercício específico para um paciente
     */
    List<WorkoutExecutionEntity> findByPatientIdAndExerciseNameOrderByRecordedAtDesc(
        UUID patientId, String exerciseName
    );
}
