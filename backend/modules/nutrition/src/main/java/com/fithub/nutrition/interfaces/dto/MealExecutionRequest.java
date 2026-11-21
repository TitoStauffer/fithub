package com.fithub.nutrition.interfaces.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de requisição para registrar execução de refeição
 */
public class MealExecutionRequest {
    @NotBlank(message = "Meal name is required")
    private String mealName; // Café da manhã, Almoço, Lanche, etc.

    @NotNull(message = "Protein is required")
    @DecimalMin(value = "0.0", message = "Protein must be positive")
    @DecimalMax(value = "1000.0", message = "Protein cannot exceed 1000g")
    private Double protein; // grams

    @NotNull(message = "Carbs is required")
    @DecimalMin(value = "0.0", message = "Carbs must be positive")
    @DecimalMax(value = "1000.0", message = "Carbs cannot exceed 1000g")
    private Double carbs; // grams

    @NotNull(message = "Fat is required")
    @DecimalMin(value = "0.0", message = "Fat must be positive")
    @DecimalMax(value = "1000.0", message = "Fat cannot exceed 1000g")
    private Double fat; // grams

    @NotNull(message = "Completed flag is required")
    private Boolean completed;

    public MealExecutionRequest() {
    }

    public MealExecutionRequest(String mealName, Double protein, Double carbs,
                              Double fat, Boolean completed) {
        this.mealName = mealName;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.completed = completed;
    }

    // Getters e Setters
    public String getMealName() { return mealName; }
    public void setMealName(String mealName) { this.mealName = mealName; }

    public Double getProtein() { return protein; }
    public void setProtein(Double protein) { this.protein = protein; }

    public Double getCarbs() { return carbs; }
    public void setCarbs(Double carbs) { this.carbs = carbs; }

    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
}
