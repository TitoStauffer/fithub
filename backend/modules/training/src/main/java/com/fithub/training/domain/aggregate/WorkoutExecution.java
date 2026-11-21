package com.fithub.training.domain.aggregate;

import com.fithub.training.domain.valueobject.ExecutionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agregado raiz WorkoutExecution: registro de execução de exercício.
 * Append-only: nunca edita/deleta.
 */
public class WorkoutExecution {
    private final UUID id;
    private final UUID patientId;
    private final UUID workoutPlanVersionId;
    private final UUID exerciseId;
    private final String dayOfWeek;
    private final Integer actualSets;
    private final Integer actualReps;
    private final Double actualWeight;
    private final String feeling;
    private final LocalDateTime executedAt;
    private final ExecutionStatus status;
    private final LocalDateTime createdAt;

    private WorkoutExecution(UUID id, UUID patientId, UUID workoutPlanVersionId, UUID exerciseId,
                            String dayOfWeek, Integer actualSets, Integer actualReps, Double actualWeight,
                            String feeling, LocalDateTime executedAt, ExecutionStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.workoutPlanVersionId = workoutPlanVersionId;
        this.exerciseId = exerciseId;
        this.dayOfWeek = dayOfWeek;
        this.actualSets = actualSets;
        this.actualReps = actualReps;
        this.actualWeight = actualWeight;
        this.feeling = feeling;
        this.executedAt = executedAt;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static WorkoutExecution create(UUID patientId, UUID workoutPlanVersionId, UUID exerciseId,
                                         String dayOfWeek, Integer actualSets, Integer actualReps,
                                         Double actualWeight, String feeling, LocalDateTime executedAt,
                                         ExecutionStatus status) {
        validateExecution(actualSets, actualReps, actualWeight);
        return new WorkoutExecution(UUID.randomUUID(), patientId, workoutPlanVersionId, exerciseId,
            dayOfWeek, actualSets, actualReps, actualWeight, feeling, executedAt, status, LocalDateTime.now());
    }

    private static void validateExecution(Integer sets, Integer reps, Double weight) {
        if (sets == null || sets < 1 || sets > 100) {
            throw new IllegalArgumentException("Sets must be between 1 and 100");
        }
        if (reps == null || reps < 1 || reps > 100) {
            throw new IllegalArgumentException("Reps must be between 1 and 100");
        }
        if (weight == null || weight < 0 || weight > 500) {
            throw new IllegalArgumentException("Weight must be between 0 and 500 kg");
        }
    }

    public static WorkoutExecution fromDatabase(UUID id, UUID patientId, UUID workoutPlanVersionId, UUID exerciseId,
                                               String dayOfWeek, Integer actualSets, Integer actualReps,
                                               Double actualWeight, String feeling, LocalDateTime executedAt,
                                               ExecutionStatus status, LocalDateTime createdAt) {
        return new WorkoutExecution(id, patientId, workoutPlanVersionId, exerciseId, dayOfWeek,
            actualSets, actualReps, actualWeight, feeling, executedAt, status, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getWorkoutPlanVersionId() { return workoutPlanVersionId; }
    public UUID getExerciseId() { return exerciseId; }
    public String getDayOfWeek() { return dayOfWeek; }
    public Integer getActualSets() { return actualSets; }
    public Integer getActualReps() { return actualReps; }
    public Double getActualWeight() { return actualWeight; }
    public String getFeeling() { return feeling; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public ExecutionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
