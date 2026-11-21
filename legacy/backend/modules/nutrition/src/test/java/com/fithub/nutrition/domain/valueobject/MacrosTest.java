package com.fithub.nutrition.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD: Cálculo automático de calorias
 * Fórmula: (protein*4) + (carbs*4) + (fat*9)
 */
class MacrosTest {

    @Test
    void shouldCreateWithValidMacros() {
        Macros macros = Macros.create(30.0, 120.0, 20.0);

        assertEquals(30.0, macros.getProtein());
        assertEquals(120.0, macros.getCarbs());
        assertEquals(20.0, macros.getFat());
    }

    @ParameterizedTest
    @CsvSource({
        "30.0, 120.0, 20.0, 780.0",    // (30*4) + (120*4) + (20*9) = 120 + 480 + 180 = 780
        "50.0, 150.0, 50.0, 1250.0",   // (50*4) + (150*4) + (50*9) = 200 + 600 + 450 = 1250
        "0.0, 0.0, 0.0, 0.0"           // 0
    })
    void shouldCalculateCaloriesCorrectly(Double protein, Double carbs, Double fat, Double expectedCals) {
        Macros macros = Macros.create(protein, carbs, fat);
        assertEquals(expectedCals, macros.getCalories());
    }

    @Test
    void shouldRejectNegativeProtein() {
        assertThrows(IllegalArgumentException.class,
            () -> Macros.create(-10.0, 100.0, 20.0));
    }

    @Test
    void shouldBeValueObject() {
        Macros macros1 = Macros.create(30.0, 120.0, 20.0);
        Macros macros2 = Macros.create(30.0, 120.0, 20.0);

        assertEquals(macros1, macros2);
    }
}
