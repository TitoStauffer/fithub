package com.fithub.nutrition.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository para NutritionMealExecutionEntity
 */
@Repository
public interface MealExecutionJpaRepository extends JpaRepository<NutritionMealExecutionEntity, UUID> {

    /**
     * Busca execuções de refeição de um paciente
     */
    List<NutritionMealExecutionEntity> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    /**
     * Busca execuções de um período específico
     */
    List<NutritionMealExecutionEntity> findByPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(
        UUID patientId, LocalDateTime startDate, LocalDateTime endDate
    );
}
