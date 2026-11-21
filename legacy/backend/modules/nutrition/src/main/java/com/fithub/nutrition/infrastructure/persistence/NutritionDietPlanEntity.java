package com.fithub.nutrition.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity para DietPlan com versionamento automático.
 * Cada atualização incrementa a versão.
 */
@Entity
@Table(name = "diet_plans")
public class NutritionDietPlanEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "nutritionist_id", nullable = false)
    private UUID nutritionistId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE"; // DRAFT, ACTIVE, ARCHIVED

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "daily_calorie_target")
    private Integer dailyCalorieTarget;

    @Column(name = "daily_protein_target")
    private Integer dailyProteinTarget;

    @Column(name = "daily_carbs_target")
    private Integer dailyCarbsTarget;

    @Column(name = "daily_fat_target")
    private Integer dailyFatTarget;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Mapeamento de refeições (serializado como JSON)
    @Column(name = "meals_json", columnDefinition = "TEXT")
    private String mealsJson = "{}";

    public NutritionDietPlanEntity() {
    }

    public NutritionDietPlanEntity(UUID id, UUID patientId, UUID nutritionistId, Integer version,
                                 String status, Boolean isActive, Integer dailyCalorieTarget,
                                 Integer dailyProteinTarget, Integer dailyCarbsTarget,
                                 Integer dailyFatTarget, LocalDateTime createdAt,
                                 LocalDateTime activatedAt, LocalDateTime updatedAt, String mealsJson) {
        this.id = id;
        this.patientId = patientId;
        this.nutritionistId = nutritionistId;
        this.version = version;
        this.status = status;
        this.isActive = isActive;
        this.dailyCalorieTarget = dailyCalorieTarget;
        this.dailyProteinTarget = dailyProteinTarget;
        this.dailyCarbsTarget = dailyCarbsTarget;
        this.dailyFatTarget = dailyFatTarget;
        this.createdAt = createdAt;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
        this.mealsJson = mealsJson;
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

    public UUID getNutritionistId() { return nutritionistId; }
    public void setNutritionistId(UUID nutritionistId) { this.nutritionistId = nutritionistId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getDailyCalorieTarget() { return dailyCalorieTarget; }
    public void setDailyCalorieTarget(Integer dailyCalorieTarget) { this.dailyCalorieTarget = dailyCalorieTarget; }

    public Integer getDailyProteinTarget() { return dailyProteinTarget; }
    public void setDailyProteinTarget(Integer dailyProteinTarget) { this.dailyProteinTarget = dailyProteinTarget; }

    public Integer getDailyCarbsTarget() { return dailyCarbsTarget; }
    public void setDailyCarbsTarget(Integer dailyCarbsTarget) { this.dailyCarbsTarget = dailyCarbsTarget; }

    public Integer getDailyFatTarget() { return dailyFatTarget; }
    public void setDailyFatTarget(Integer dailyFatTarget) { this.dailyFatTarget = dailyFatTarget; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getMealsJson() { return mealsJson; }
    public void setMealsJson(String mealsJson) { this.mealsJson = mealsJson; }
}
