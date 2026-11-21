package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.aggregate.Exercise;
import com.fithub.training.domain.valueobject.MuscleGroup;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA Entity para Exercise (catálogo de exercícios do ExerciseDB).
 * Read-only cache: dados sincronizados via integração externa.
 */
@Entity
@Table(name = "exercises")
public class ExerciseEntity {
    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "muscle_groups", nullable = false)
    private String muscleGroupsStr; // Stored as comma-separated string

    @Column(name = "equipment", length = 100)
    private String equipment;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "gif_url")
    private String gifUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ExerciseEntity() {
    }

    public ExerciseEntity(UUID id, String externalId, String name, String muscleGroupsStr,
                        String equipment, String videoUrl, String gifUrl, String description,
                        LocalDateTime createdAt) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.muscleGroupsStr = muscleGroupsStr;
        this.equipment = equipment;
        this.videoUrl = videoUrl;
        this.gifUrl = gifUrl;
        this.description = description;
        this.createdAt = createdAt;
    }

    /**
     * Converte Entity para Aggregate
     */
    public Exercise toAggregate() {
        List<MuscleGroup> muscleGroups = parseMuscleGroups(muscleGroupsStr);
        return Exercise.fromDatabase(id, externalId, name, muscleGroups, equipment,
            videoUrl, gifUrl, description, createdAt);
    }

    /**
     * Converte Aggregate para Entity
     */
    public static ExerciseEntity fromAggregate(Exercise exercise) {
        String muscleGroupsStr = exercise.getMuscleGroups().stream()
            .map(MuscleGroup::name)
            .collect(Collectors.joining(","));

        return new ExerciseEntity(
            exercise.getId(),
            exercise.getExternalId(),
            exercise.getName(),
            muscleGroupsStr,
            exercise.getEquipment(),
            exercise.getVideoUrl(),
            exercise.getGifUrl(),
            exercise.getDescription(),
            exercise.getCreatedAt()
        );
    }

    private static List<MuscleGroup> parseMuscleGroups(String str) {
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(str.split(","))
            .map(String::trim)
            .map(MuscleGroup::valueOf)
            .collect(Collectors.toList());
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMuscleGroupsStr() { return muscleGroupsStr; }
    public void setMuscleGroupsStr(String muscleGroupsStr) { this.muscleGroupsStr = muscleGroupsStr; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
