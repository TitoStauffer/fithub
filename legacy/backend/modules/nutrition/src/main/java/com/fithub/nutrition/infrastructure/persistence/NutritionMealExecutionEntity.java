package com.fithub.nutrition.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity para MealExecution (registro de execução de refeição).
 * Append-only: histórico preservado.
 */
@Entity
@Table(name = "meal_executions")
public class NutritionMealExecutionEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "meal_name", nullable = false, length = 255)
    private String mealName; // Café da manhã, Almoço, Lanche, etc.

    @Column(name = "protein", nullable = false)
    private Double protein; // grams

    @Column(name = "carbs", nullable = false)
    private Double carbs; // grams

    @Column(name = "fat", nullable = false)
    private Double fat; // grams

    @Column(name = "calories")
    private Integer calories; // auto-calculated

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public NutritionMealExecutionEntity() {
    }

    public NutritionMealExecutionEntity(UUID id, UUID patientId, String mealName,
                                      Double protein, Double carbs, Double fat,
                                      Integer calories, Boolean completed,
                                      LocalDateTime recordedAt) {
        this.id = id;
        this.patientId = patientId;
        this.mealName = mealName;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.calories = calories;
        this.completed = completed;
        this.recordedAt = recordedAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.calories == null) {
            this.calories = (int) ((protein * 4) + (carbs * 4) + (fat * 9));
        }
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getMealName() { return mealName; }
    public void setMealName(String mealName) { this.mealName = mealName; }

    public Double getProtein() { return protein; }
    public void setProtein(Double protein) { this.protein = protein; }

    public Double getCarbs() { return carbs; }
    public void setCarbs(Double carbs) { this.carbs = carbs; }

    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }

    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
