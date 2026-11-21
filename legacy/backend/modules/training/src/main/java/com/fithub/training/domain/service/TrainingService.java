package com.fithub.training.domain.service;

import com.fithub.training.domain.aggregate.Exercise;
import com.fithub.training.domain.aggregate.WorkoutExecution;
import com.fithub.training.domain.aggregate.WorkoutPlan;
import com.fithub.training.domain.aggregate.WorkoutExerciseDetails;
import com.fithub.training.domain.repository.ExerciseRepository;
import com.fithub.training.domain.repository.WorkoutPlanRepository;
import com.fithub.training.domain.repository.WorkoutExecutionRepository;
import com.fithub.training.domain.valueobject.ExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TrainingService {
    private static final Logger logger = LoggerFactory.getLogger(TrainingService.class);
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutExecutionRepository workoutExecutionRepository;
    private final ExerciseRepository exerciseRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TrainingService(WorkoutPlanRepository workoutPlanRepository,
                          WorkoutExecutionRepository workoutExecutionRepository,
                          ExerciseRepository exerciseRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutExecutionRepository = workoutExecutionRepository;
        this.exerciseRepository = exerciseRepository;
        this.eventPublisher = eventPublisher;
    }

    public WorkoutPlan createWorkoutPlan(UUID patientId, UUID ptId) {
        logger.debug("Creating workout plan for patient: {}", patientId);
        WorkoutPlan plan = WorkoutPlan.create(patientId, ptId);
        workoutPlanRepository.save(plan);
        return plan;
    }

    public WorkoutExecution recordExecution(UUID patientId, UUID exerciseId, String dayOfWeek,
                                           Integer sets, Integer reps, Double weight, String feeling) {
        logger.debug("Recording execution: patient={}, exercise={}", patientId, exerciseId);

        WorkoutPlan plan = workoutPlanRepository.findByPatientIdAndLatestVersion(patientId)
            .orElseThrow(() -> new IllegalArgumentException("No active workout plan"));

        WorkoutExecution execution = WorkoutExecution.create(patientId, plan.getId(), exerciseId,
            dayOfWeek, sets, reps, weight, feeling, LocalDateTime.now(), ExecutionStatus.COMPLETED);

        workoutExecutionRepository.save(execution);
        logger.info("Execution recorded: id={}", execution.getId());
        return execution;
    }
}
