package com.fithub.nutrition.domain.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado raiz DietPlan: prescrição de dieta do nutricionista.
 * Versionado (histórico preservado).
 */
public class DietPlan {
    private final UUID id;
    private final UUID patientId;
    private final UUID nutritionistId;
    private final Integer version;
    private final List<Meal> meals;
    private final Boolean isActive;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime activatedAt;
    private LocalDateTime updatedAt;

    private DietPlan(UUID id, UUID patientId, UUID nutritionistId, Integer version,
                    List<Meal> meals, Boolean isActive, String status,
                    LocalDateTime createdAt, LocalDateTime activatedAt, LocalDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.nutritionistId = nutritionistId;
        this.version = version;
        this.meals = meals;
        this.isActive = isActive;
        this.status = status;
        this.createdAt = createdAt;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
    }

    public static DietPlan create(UUID patientId, UUID nutritionistId) {
        LocalDateTime now = LocalDateTime.now();
        return new DietPlan(UUID.randomUUID(), patientId, nutritionistId, 1, new ArrayList<>(),
            true, "ACTIVE", now, now, now);
    }

    public static DietPlan fromDatabase(UUID id, UUID patientId, UUID nutritionistId, Integer version,
                                       List<Meal> meals, Boolean isActive, String status,
                                       LocalDateTime createdAt, LocalDateTime activatedAt,
                                       LocalDateTime updatedAt) {
        return new DietPlan(id, patientId, nutritionistId, version, meals, isActive, status, createdAt, activatedAt, updatedAt);
    }

    public void addMeal(Meal meal) {
        meals.add(meal);
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getNutritionistId() { return nutritionistId; }
    public Integer getVersion() { return version; }
    public List<Meal> getMeals() { return new ArrayList<>(meals); }
    public Boolean getIsActive() { return isActive; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
