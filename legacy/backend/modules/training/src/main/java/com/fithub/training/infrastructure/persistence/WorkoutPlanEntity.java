package com.fithub.training.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JPA Entity para WorkoutPlan com versionamento automático.
 * Cada atualização incrementa a versão.
 */
@Entity
@Table(name = "workout_plans")
public class WorkoutPlanEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "pt_id", nullable = false)
    private UUID ptId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE"; // DRAFT, ACTIVE, ARCHIVED

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Mapeamento de exercícios por dia (serializado como JSON)
    @Column(name = "exercises_json", columnDefinition = "TEXT")
    private String exercisesJson = "{}";

    public WorkoutPlanEntity() {
    }

    public WorkoutPlanEntity(UUID id, UUID patientId, UUID ptId, Integer version, String status,
                            Boolean isActive, LocalDateTime createdAt, LocalDateTime activatedAt,
                            LocalDateTime updatedAt, String exercisesJson) {
        this.id = id;
        this.patientId = patientId;
        this.ptId = ptId;
        this.version = version;
        this.status = status;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
        this.exercisesJson = exercisesJson;
    }

    @PreUpdate
    public void preUpdate() {
        this.version = (this.version == null ? 1 : this.version + 1);
        this.updatedAt = LocalDateTime.now();
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public UUID getPtId() { return ptId; }
    public void setPtId(UUID ptId) { this.ptId = ptId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getExercisesJson() { return exercisesJson; }
    public void setExercisesJson(String exercisesJson) { this.exercisesJson = exercisesJson; }
}
