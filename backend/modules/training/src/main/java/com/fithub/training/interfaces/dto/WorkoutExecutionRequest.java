package com.fithub.training.interfaces.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de requisição para registrar execução de treino
 */
public class WorkoutExecutionRequest {
    @NotBlank(message = "Exercise name is required")
    private String exerciseName;

    @NotNull(message = "Sets is required")
    @Min(value = 1, message = "Sets must be at least 1")
    @Max(value = 100, message = "Sets cannot exceed 100")
    private Integer sets;

    @NotNull(message = "Reps is required")
    @Min(value = 1, message = "Reps must be at least 1")
    @Max(value = 100, message = "Reps cannot exceed 100")
    private Integer reps;

    @NotNull(message = "Weight is required")
    @Min(value = 0, message = "Weight cannot be negative")
    @Max(value = 500, message = "Weight cannot exceed 500 kg")
    private Double weight;

    @NotBlank(message = "Feeling is required")
    private String feeling; // Muito Difícil, Difícil, Bom, Ótimo, Fácil

    @NotBlank(message = "Status is required")
    private String status; // COMPLETED, PARTIAL, SKIPPED

    public WorkoutExecutionRequest() {
    }

    public WorkoutExecutionRequest(String exerciseName, Integer sets, Integer reps,
                                   Double weight, String feeling, String status) {
        this.exerciseName = exerciseName;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
        this.feeling = feeling;
        this.status = status;
    }

    // Getters e Setters
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
}
