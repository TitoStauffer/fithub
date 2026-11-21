package com.fithub.nutrition.interfaces.dto;

/**
 * DTO de resposta para execução de refeição registrada
 */
public class MealExecutionResponse {
    private String id;
    private String mealName;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Integer calories;
    private Boolean completed;

    public MealExecutionResponse(String id, String mealName, Double protein,
                               Double carbs, Double fat, Integer calories, Boolean completed) {
        this.id = id;
        this.mealName = mealName;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.calories = calories;
        this.completed = completed;
    }

    // Getters
    public String getId() { return id; }
    public String getMealName() { return mealName; }
    public Double getProtein() { return protein; }
    public Double getCarbs() { return carbs; }
    public Double getFat() { return fat; }
    public Integer getCalories() { return calories; }
    public Boolean getCompleted() { return completed; }
}
