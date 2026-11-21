package com.fithub.training.interfaces.dto;

/**
 * DTO de resposta para execução de treino registrada
 */
public class WorkoutExecutionResponse {
    private String id;
    private String exerciseName;
    private Integer sets;
    private Integer reps;
    private Double weight;
    private String feeling;
    private String status;

    public WorkoutExecutionResponse(String id, String exerciseName, Integer sets,
                                  Integer reps, Double weight, String feeling, String status) {
        this.id = id;
        this.exerciseName = exerciseName;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
        this.feeling = feeling;
        this.status = status;
    }

    // Getters
    public String getId() { return id; }
    public String getExerciseName() { return exerciseName; }
    public Integer getSets() { return sets; }
    public Integer getReps() { return reps; }
    public Double getWeight() { return weight; }
    public String getFeeling() { return feeling; }
    public String getStatus() { return status; }
}
