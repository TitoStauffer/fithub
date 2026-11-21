package com.fithub.training.domain.aggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity: detalhes de um exercício na ficha de treino.
 */
public class WorkoutExerciseDetails {
    private final UUID id;
    private final UUID exerciseId;
    private final Integer sets;
    private final Integer reps;
    private final Double weight;
    private final List<UUID> alternativeExerciseIds;
    private final String videoUrl;
    private final String notes;
    private final Integer order;

    private WorkoutExerciseDetails(UUID id, UUID exerciseId, Integer sets, Integer reps, Double weight,
                                  List<UUID> alternativeExerciseIds, String videoUrl, String notes, Integer order) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
        this.alternativeExerciseIds = alternativeExerciseIds;
        this.videoUrl = videoUrl;
        this.notes = notes;
        this.order = order;
    }

    public static WorkoutExerciseDetails create(UUID exerciseId, Integer sets, Integer reps, Double weight,
                                               String videoUrl, String notes, Integer order) {
        return new WorkoutExerciseDetails(UUID.randomUUID(), exerciseId, sets, reps, weight,
            new ArrayList<>(), videoUrl, notes, order);
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public Integer getSets() { return sets; }
    public Integer getReps() { return reps; }
    public Double getWeight() { return weight; }
    public List<UUID> getAlternativeExerciseIds() { return new ArrayList<>(alternativeExerciseIds); }
    public String getVideoUrl() { return videoUrl; }
    public String getNotes() { return notes; }
    public Integer getOrder() { return order; }
}
