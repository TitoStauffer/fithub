package com.fithub.nutrition.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DEPRECATED: Use NutritionMealExecutionEntity instead.
 * Keeping this class for backward compatibility but not mapped to database.
 */
public class MealExecutionEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "diet_plan_id", nullable = false)
    private UUID dietPlanId;

    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    @Column(name = "executed_at", nullable = false)
    private LocalDate executedAt;

    @Column(name = "completed", nullable = false)
    private Boolean completed;

    @Column(name = "protein", nullable = false)
    private Double protein;

    @Column(name = "carbs", nullable = false)
    private Double carbs;

    @Column(name = "fat", nullable = false)
    private Double fat;

    @Column(name = "notes")
    private String notes;

    @Column(name = "feeling")
    private String feeling;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Getters/Setters omitted for brevity
}
