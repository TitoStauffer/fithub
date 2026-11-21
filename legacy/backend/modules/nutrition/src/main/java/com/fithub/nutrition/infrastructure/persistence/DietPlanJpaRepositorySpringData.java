package com.fithub.nutrition.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository para NutritionDietPlanEntity
 */
@Repository
public interface DietPlanJpaRepositorySpringData extends JpaRepository<NutritionDietPlanEntity, UUID> {

    /**
     * Busca o plano de dieta ativo mais recente (maior versão) de um paciente
     */
    @Query("SELECT d FROM NutritionDietPlanEntity d " +
           "WHERE d.patientId = :patientId AND d.isActive = true " +
           "ORDER BY d.version DESC LIMIT 1")
    Optional<NutritionDietPlanEntity> findLatestActiveByPatientId(@Param("patientId") UUID patientId);

    /**
     * Busca todos os planos de dieta de um paciente ordenados por versão descrescente
     */
    List<NutritionDietPlanEntity> findByPatientIdOrderByVersionDesc(UUID patientId);

    /**
     * Busca todos os planos de um nutricionista
     */
    List<NutritionDietPlanEntity> findByNutritionistId(UUID nutritionistId);
}
