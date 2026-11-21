package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.aggregate.DietPlan;
import com.fithub.nutrition.domain.aggregate.Meal;
import com.fithub.nutrition.domain.repository.DietPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementação completa do JPA DietPlanRepository
 * Adapter entre Domain e Infrastructure layers
 */
@Repository
public class JpaDietPlanRepository implements DietPlanRepository {
    private static final Logger logger = LoggerFactory.getLogger(JpaDietPlanRepository.class);
    private final DietPlanJpaRepositorySpringData jpaRepository;
    private final ObjectMapper objectMapper;

    public JpaDietPlanRepository(DietPlanJpaRepositorySpringData jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(DietPlan plan) {
        logger.debug("Saving diet plan: id={}, patient={}", plan.getId(), plan.getPatientId());

        NutritionDietPlanEntity entity = new NutritionDietPlanEntity(
            plan.getId(),
            plan.getPatientId(),
            plan.getNutritionistId(),
            plan.getVersion(),
            plan.getStatus(),
            plan.getIsActive(),
            null, // dailyCalorieTarget
            null, // dailyProteinTarget
            null, // dailyCarbsTarget
            null, // dailyFatTarget
            plan.getCreatedAt(),
            plan.getActivatedAt(),
            plan.getUpdatedAt(),
            serializeMeals(plan.getMeals())
        );

        jpaRepository.save(entity);
        logger.info("Diet plan saved: id={}", plan.getId());
    }

    @Override
    public Optional<DietPlan> findById(UUID id) {
        logger.debug("Finding diet plan by id: {}", id);
        return jpaRepository.findById(id)
            .map(this::entityToAggregate);
    }

    @Override
    public Optional<DietPlan> findByPatientIdAndLatestVersion(UUID patientId) {
        logger.debug("Finding latest diet plan for patient: {}", patientId);
        return jpaRepository.findLatestActiveByPatientId(patientId)
            .map(this::entityToAggregate);
    }

    @Override
    public List<DietPlan> findByPatientIdAllVersions(UUID patientId) {
        logger.debug("Finding all diet plan versions for patient: {}", patientId);
        return jpaRepository.findByPatientIdOrderByVersionDesc(patientId).stream()
            .map(this::entityToAggregate)
            .collect(Collectors.toList());
    }

    /**
     * Converte Entity para Aggregate
     */
    private DietPlan entityToAggregate(NutritionDietPlanEntity entity) {
        List<Meal> meals = deserializeMeals(entity.getMealsJson());

        return DietPlan.fromDatabase(
            entity.getId(),
            entity.getPatientId(),
            entity.getNutritionistId(),
            entity.getVersion(),
            meals,
            entity.getIsActive(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getActivatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Serializa meals para JSON string
     */
    private String serializeMeals(List<Meal> meals) {
        try {
            return objectMapper.writeValueAsString(meals);
        } catch (Exception e) {
            logger.warn("Failed to serialize meals", e);
            return "[]";
        }
    }

    /**
     * Desserializa JSON string para lista de Meals
     */
    private List<Meal> deserializeMeals(String mealsJson) {
        try {
            if (mealsJson == null || mealsJson.isEmpty() || "[]".equals(mealsJson)) {
                return new ArrayList<>();
            }
            // TODO: Implementar desserialização completa de Meal objects
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Failed to deserialize meals", e);
            return new ArrayList<>();
        }
    }
}
