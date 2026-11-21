package com.fithub.training.domain.aggregate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregado raiz WorkoutPlan: ficha de treino prescrita pelo PT.
 * Versionado (histórico preservado).
 */
public class WorkoutPlan {
    private final UUID id;
    private final UUID patientId;
    private final UUID ptId;
    private final Integer version;
    private final Map<String, List<WorkoutExerciseDetails>> days;  // Map<DayOfWeek, List<Exercise>>
    private final Boolean isActive;
    private final String status;  // DRAFT, ACTIVE, ARCHIVED
    private final LocalDateTime createdAt;
    private final LocalDateTime activatedAt;
    private LocalDateTime updatedAt;

    private WorkoutPlan(UUID id, UUID patientId, UUID ptId, Integer version,
                       Map<String, List<WorkoutExerciseDetails>> days, Boolean isActive, String status,
                       LocalDateTime createdAt, LocalDateTime activatedAt, LocalDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.ptId = ptId;
        this.version = version;
        this.days = days;
        this.isActive = isActive;
        this.status = status;
        this.createdAt = createdAt;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
    }

    public static WorkoutPlan create(UUID patientId, UUID ptId) {
        LocalDateTime now = LocalDateTime.now();
        return new WorkoutPlan(UUID.randomUUID(), patientId, ptId, 1, new LinkedHashMap<>(),
            true, "ACTIVE", now, now, now);
    }

    public static WorkoutPlan fromDatabase(UUID id, UUID patientId, UUID ptId, Integer version,
                                          Map<String, List<WorkoutExerciseDetails>> days, Boolean isActive,
                                          String status, LocalDateTime createdAt, LocalDateTime activatedAt,
                                          LocalDateTime updatedAt) {
        return new WorkoutPlan(id, patientId, ptId, version, days, isActive, status, createdAt, activatedAt, updatedAt);
    }

    public void addExerciseToDay(String day, WorkoutExerciseDetails exercise) {
        days.computeIfAbsent(day, k -> new ArrayList<>()).add(exercise);
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getPtId() { return ptId; }
    public Integer getVersion() { return version; }
    public Map<String, List<WorkoutExerciseDetails>> getDays() { return new LinkedHashMap<>(days); }
    public Boolean getIsActive() { return isActive; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
