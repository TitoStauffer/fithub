package com.fithub.training.domain.aggregate;

import com.fithub.training.domain.valueobject.MuscleGroup;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado raiz Exercise: catálogo de exercícios (cache de ExerciseDB).
 */
public class Exercise {
    private final UUID id;
    private final String externalId;  // ID do ExerciseDB
    private final String name;
    private final List<MuscleGroup> muscleGroups;
    private final String equipment;
    private final String videoUrl;
    private final String gifUrl;
    private final String description;
    private final LocalDateTime createdAt;

    private Exercise(UUID id, String externalId, String name, List<MuscleGroup> muscleGroups,
                    String equipment, String videoUrl, String gifUrl, String description,
                    LocalDateTime createdAt) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.muscleGroups = muscleGroups;
        this.equipment = equipment;
        this.videoUrl = videoUrl;
        this.gifUrl = gifUrl;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static Exercise create(String externalId, String name, List<MuscleGroup> muscleGroups,
                                 String equipment, String videoUrl, String gifUrl, String description) {
        return new Exercise(UUID.randomUUID(), externalId, name, new ArrayList<>(muscleGroups),
            equipment, videoUrl, gifUrl, description, LocalDateTime.now());
    }

    public static Exercise fromDatabase(UUID id, String externalId, String name, List<MuscleGroup> muscleGroups,
                                       String equipment, String videoUrl, String gifUrl, String description,
                                       LocalDateTime createdAt) {
        return new Exercise(id, externalId, name, muscleGroups, equipment, videoUrl, gifUrl, description, createdAt);
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public List<MuscleGroup> getMuscleGroups() { return new ArrayList<>(muscleGroups); }
    public String getEquipment() { return equipment; }
    public String getVideoUrl() { return videoUrl; }
    public String getGifUrl() { return gifUrl; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
