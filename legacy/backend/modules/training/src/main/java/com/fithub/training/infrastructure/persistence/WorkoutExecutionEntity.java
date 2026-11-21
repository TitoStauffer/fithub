package com.fithub.training.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity para WorkoutExecution (registro de execução de treino).
 * Append-only: histórico preservado.
 */
@Entity
@Table(name = "workout_executions")
public class WorkoutExecutionEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "exercise_name", nullable = false, length = 255)
    private String exerciseName;

    @Column(name = "sets", nullable = false)
    private Integer sets;

    @Column(name = "reps", nullable = false)
    private Integer reps;

    @Column(name = "weight", nullable = false)
    private Double weight; // kg

    @Column(name = "feeling", length = 50)
    private String feeling; // Muito Difícil, Difícil, Bom, Ótimo, Fácil

    @Column(name = "status", nullable = false, length = 50)
    private String status; // COMPLETED, PARTIAL, SKIPPED

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public WorkoutExecutionEntity() {
    }

    public WorkoutExecutionEntity(UUID id, UUID patientId, String exerciseName, Integer sets,
                                Integer reps, Double weight, String feeling, String status,
                                LocalDateTime recordedAt) {
        this.id = id;
        this.patientId = patientId;
        this.exerciseName = exerciseName;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
        this.feeling = feeling;
        this.status = status;
        this.recordedAt = recordedAt;
    }


    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public Integer getSets() { return sets; }
    public void setSets(Integer sets) { this.sets = sets; }

    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getFeeling() { return feeling; }
    public void setFeeling(String feeling) { this.feeling = feeling; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
