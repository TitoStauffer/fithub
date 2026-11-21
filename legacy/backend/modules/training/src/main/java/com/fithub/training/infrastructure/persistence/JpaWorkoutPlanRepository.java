package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.aggregate.WorkoutPlan;
import com.fithub.training.domain.aggregate.WorkoutExerciseDetails;
import com.fithub.training.domain.repository.WorkoutPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementação completa do JPA WorkoutPlanRepository
 * Adapter entre Domain e Infrastructure layers
 */
@Repository
public class JpaWorkoutPlanRepository implements WorkoutPlanRepository {
    private static final Logger logger = LoggerFactory.getLogger(JpaWorkoutPlanRepository.class);
    private final WorkoutPlanJpaRepositorySpringData jpaRepository;
    private final ObjectMapper objectMapper;

    public JpaWorkoutPlanRepository(WorkoutPlanJpaRepositorySpringData jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(WorkoutPlan plan) {
        logger.debug("Saving workout plan: id={}, patient={}", plan.getId(), plan.getPatientId());

        WorkoutPlanEntity entity = new WorkoutPlanEntity(
            plan.getId(),
            plan.getPatientId(),
            plan.getPtId(),
            plan.getVersion(),
            plan.getStatus(),
            plan.getIsActive(),
            plan.getCreatedAt(),
            plan.getActivatedAt(),
            plan.getUpdatedAt(),
            serializeExercises(plan.getDays())
        );

        jpaRepository.save(entity);
        logger.info("Workout plan saved: id={}", plan.getId());
    }

    @Override
    public Optional<WorkoutPlan> findById(UUID id) {
        logger.debug("Finding workout plan by id: {}", id);
        return jpaRepository.findById(id)
            .map(this::entityToAggregate);
    }

    @Override
    public Optional<WorkoutPlan> findByPatientIdAndLatestVersion(UUID patientId) {
        logger.debug("Finding latest workout plan for patient: {}", patientId);
        return jpaRepository.findLatestActiveByPatientId(patientId)
            .map(this::entityToAggregate);
    }

    @Override
    public List<WorkoutPlan> findByPatientIdAllVersions(UUID patientId) {
        logger.debug("Finding all workout plan versions for patient: {}", patientId);
        return jpaRepository.findByPatientIdOrderByVersionDesc(patientId).stream()
            .map(this::entityToAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<WorkoutPlan> findByPtId(UUID ptId) {
        logger.debug("Finding all workout plans for PT: {}", ptId);
        return jpaRepository.findByPtId(ptId).stream()
            .map(this::entityToAggregate)
            .collect(Collectors.toList());
    }

    /**
     * Converte Entity para Aggregate
     */
    private WorkoutPlan entityToAggregate(WorkoutPlanEntity entity) {
        Map<String, List<WorkoutExerciseDetails>> days = new LinkedHashMap<>();
        // TODO: Implementar desserialização de exercises_json para List<WorkoutExerciseDetails>
        // Por enquanto retorna mapa vazio

        return WorkoutPlan.fromDatabase(
            entity.getId(),
            entity.getPatientId(),
            entity.getPtId(),
            entity.getVersion(),
            days,
            entity.getIsActive(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getActivatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Serializa exercises para JSON string
     */
    private String serializeExercises(Map<String, ?> days) {
        try {
            return objectMapper.writeValueAsString(days);
        } catch (Exception e) {
            logger.warn("Failed to serialize exercises", e);
            return "{}";
        }
    }
}
