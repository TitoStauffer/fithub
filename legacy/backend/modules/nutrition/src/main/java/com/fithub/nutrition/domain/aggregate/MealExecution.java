package com.fithub.nutrition.domain.aggregate;

import com.fithub.nutrition.domain.valueobject.Macros;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agregado raiz MealExecution: registro de execução de refeição.
 * Append-only: nunca edita/deleta.
 */
public class MealExecution {
    private final UUID id;
    private final UUID patientId;
    private final UUID dietPlanId;
    private final UUID mealId;
    private final LocalDate executedAt;
    private final Boolean completed;
    private final Macros macros;
    private final String notes;
    private final String feeling;
    private final String status;
    private final LocalDateTime createdAt;

    private MealExecution(UUID id, UUID patientId, UUID dietPlanId, UUID mealId, LocalDate executedAt,
                         Boolean completed, Macros macros, String notes, String feeling, String status,
                         LocalDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.dietPlanId = dietPlanId;
        this.mealId = mealId;
        this.executedAt = executedAt;
        this.completed = completed;
        this.macros = macros;
        this.notes = notes;
        this.feeling = feeling;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static MealExecution create(UUID patientId, UUID dietPlanId, UUID mealId, LocalDate executedAt,
                                      Boolean completed, Macros macros, String notes, String feeling) {
        String status = completed ? "COMPLETED" : "PARTIAL";
        if (notes != null && !notes.isEmpty()) status = "DEVIATED";
        return new MealExecution(UUID.randomUUID(), patientId, dietPlanId, mealId, executedAt,
            completed, macros, notes, feeling, status, LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getDietPlanId() { return dietPlanId; }
    public UUID getMealId() { return mealId; }
    public LocalDate getExecutedAt() { return executedAt; }
    public Boolean getCompleted() { return completed; }
    public Macros getMacros() { return macros; }
    public String getNotes() { return notes; }
    public String getFeeling() { return feeling; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
