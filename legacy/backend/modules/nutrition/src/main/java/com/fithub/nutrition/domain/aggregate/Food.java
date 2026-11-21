package com.fithub.nutrition.domain.aggregate;

import com.fithub.nutrition.domain.valueobject.Macros;
import java.util.UUID;

/**
 * Entity: alimento dentro de uma refeição.
 */
public class Food {
    private final UUID id;
    private final String name;
    private final Double quantity;
    private final String unit;
    private final Macros macros;
    private final String notes;

    private Food(UUID id, String name, Double quantity, String unit, Macros macros, String notes) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.macros = macros;
        this.notes = notes;
    }

    public static Food create(String name, Double quantity, String unit, Double protein, Double carbs, Double fat, String notes) {
        Macros macros = Macros.create(protein, carbs, fat);
        return new Food(UUID.randomUUID(), name, quantity, unit, macros, notes);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public Macros getMacros() { return macros; }
    public String getNotes() { return notes; }
}
