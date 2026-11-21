package com.fithub.nutrition.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository para NutritionMealExecutionEntity
 */
@Repository
public interface NutritionMealExecutionJpaRepository extends JpaRepository<NutritionMealExecutionEntity, UUID> {
    /**
     * Busca todas as execuções de refeição de um paciente
     */
    @Query("SELECT me FROM NutritionMealExecutionEntity me WHERE me.patientId = :patientId ORDER BY me.recordedAt DESC")
    List<NutritionMealExecutionEntity> findByPatientId(@Param("patientId") UUID patientId);

    /**
     * Busca execuções de refeição em um dia específico
     */
    @Query("SELECT me FROM NutritionMealExecutionEntity me WHERE me.patientId = :patientId AND CAST(me.recordedAt AS date) = :date ORDER BY me.recordedAt")
    List<NutritionMealExecutionEntity> findByPatientIdAndDate(@Param("patientId") UUID patientId, @Param("date") LocalDate date);

    /**
     * Busca execuções recentes (últimos 30 dias)
     */
    @Query("SELECT me FROM NutritionMealExecutionEntity me WHERE me.patientId = :patientId AND me.recordedAt >= CURRENT_TIMESTAMP - 30 ORDER BY me.recordedAt DESC")
    List<NutritionMealExecutionEntity> findRecentExecutions(@Param("patientId") UUID patientId);
}
