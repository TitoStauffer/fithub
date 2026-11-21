package com.fithub.nutrition.domain.aggregate;

import com.fithub.nutrition.domain.valueobject.Macros;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity: refeição prescrita no plano de dieta.
 */
public class Meal {
    private final UUID id;
    private final String name;
    private final Integer order;
    private final List<Food> foods;
    private final Macros macros;

    private Meal(UUID id, String name, Integer order, List<Food> foods, Macros macros) {
        this.id = id;
        this.name = name;
        this.order = order;
        this.foods = foods;
        this.macros = macros;
    }

    public static Meal create(String name, Integer order, List<Food> foods) {
        Macros macros = calculateMacros(foods);
        return new Meal(UUID.randomUUID(), name, order, foods, macros);
    }

    private static Macros calculateMacros(List<Food> foods) {
        double totalProtein = foods.stream().mapToDouble(f -> f.getMacros().getProtein()).sum();
        double totalCarbs = foods.stream().mapToDouble(f -> f.getMacros().getCarbs()).sum();
        double totalFat = foods.stream().mapToDouble(f -> f.getMacros().getFat()).sum();
        return Macros.create(totalProtein, totalCarbs, totalFat);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Integer getOrder() { return order; }
    public List<Food> getFoods() { return new ArrayList<>(foods); }
    public Macros getMacros() { return macros; }
}
