package com.fithub.nutrition.domain.valueobject;

import java.util.Objects;

/**
 * Value Object: macronutrientes (proteína, carboidrato, gordura, calorias).
 * Calorias calculadas automaticamente: (protein*4) + (carbs*4) + (fat*9)
 */
public class Macros {
    private final Double protein;  // gramas
    private final Double carbs;    // gramas
    private final Double fat;      // gramas
    private final Double calories; // kcal (calculado)

    private Macros(Double protein, Double carbs, Double fat) {
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.calories = calculateCalories(protein, carbs, fat);
    }

    public static Macros create(Double protein, Double carbs, Double fat) {
        if (protein == null || protein < 0) throw new IllegalArgumentException("Protein must be >= 0");
        if (carbs == null || carbs < 0) throw new IllegalArgumentException("Carbs must be >= 0");
        if (fat == null || fat < 0) throw new IllegalArgumentException("Fat must be >= 0");
        return new Macros(protein, carbs, fat);
    }

    private static Double calculateCalories(Double protein, Double carbs, Double fat) {
        return Math.round((protein * 4 + carbs * 4 + fat * 9) * 100.0) / 100.0;
    }

    public Double getProtein() { return protein; }
    public Double getCarbs() { return carbs; }
    public Double getFat() { return fat; }
    public Double getCalories() { return calories; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Macros macros = (Macros) o;
        return Objects.equals(protein, macros.protein) &&
               Objects.equals(carbs, macros.carbs) &&
               Objects.equals(fat, macros.fat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protein, carbs, fat);
    }
}
