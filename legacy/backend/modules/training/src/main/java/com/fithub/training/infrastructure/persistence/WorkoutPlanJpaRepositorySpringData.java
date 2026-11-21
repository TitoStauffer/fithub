package com.fithub.training.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository para WorkoutPlanEntity
 */
@Repository
public interface WorkoutPlanJpaRepositorySpringData extends JpaRepository<WorkoutPlanEntity, UUID> {

    /**
     * Busca o plano de treino ativo mais recente (maior versão) de um paciente
     */
    @Query("SELECT w FROM WorkoutPlanEntity w " +
           "WHERE w.patientId = :patientId AND w.isActive = true " +
           "ORDER BY w.version DESC LIMIT 1")
    Optional<WorkoutPlanEntity> findLatestActiveByPatientId(@Param("patientId") UUID patientId);

    /**
     * Busca todos os planos de um paciente ordenados por versão descrescente
     */
    List<WorkoutPlanEntity> findByPatientIdOrderByVersionDesc(UUID patientId);

    /**
     * Busca todos os planos de um PT
     */
    List<WorkoutPlanEntity> findByPtId(UUID ptId);
}
