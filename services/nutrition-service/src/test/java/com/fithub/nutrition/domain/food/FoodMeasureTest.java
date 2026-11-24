package com.fithub.nutrition.domain.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Medida caseira e a conversão para gramas e nutrientes da porção (DEV-0076). */
class FoodMeasureTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID NUTRI = UUID.randomUUID();

    private static FoodMeasure measure(String name, String amount) {
        return new FoodMeasure(
                UUID.randomUUID(), UUID.randomUUID(), NUTRI, name, amount == null ? null : new BigDecimal(amount), NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidFoodException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void duasColheresDe25GDeUmAlimentoCom10GDeProteinaDao50GE5GDeProteina() {
        FoodMeasure spoon = measure("  Colher de sopa  ", "25");
        assertThat(spoon.name()).isEqualTo("Colher de sopa");
        BigDecimal grams = spoon.toBaseUnit(new BigDecimal("2"));
        assertThat(grams).isEqualByComparingTo("50");

        Food food = new Food(
                UUID.randomUUID(),
                Food.Kind.OWN,
                NUTRI,
                "Pasta de amendoim",
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("588"), new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("50"), null),
                true,
                null,
                NOW,
                NOW);
        Food.Amounts portion = food.nutrientsIn(grams);
        assertThat(portion.proteinG()).isEqualByComparingTo("5");
        assertThat(portion.energyKcal()).isEqualByComparingTo("294");
        assertThat(portion.fatG()).isEqualByComparingTo("25");
        assertThat(portion.fiberG()).isNull();
        // Meia medida e arredondamento a 1 casa: 0,5 × 25 g = 12,5 g; 588 × 12,5 / 100 = 73,5 kcal.
        assertThat(food.nutrientsIn(spoon.toBaseUnit(new BigDecimal("0.5"))).energyKcal())
                .isEqualByComparingTo("73.5");
    }

    @Test
    void nomeEValorDentroDasFaixas() {
        assertThat(measure("Unidade", "0.1").amount()).isEqualByComparingTo("0.1");
        assertThat(measure("Fatia", "2000").amount()).isEqualByComparingTo("2000");
        assertInvalid("name", () -> measure("a", "10"));
        assertInvalid("name", () -> measure("a".repeat(41), "10"));
        assertInvalid("amount", () -> measure("Fatia", null));
        assertInvalid("amount", () -> measure("Fatia", "0.09"));
        assertInvalid("amount", () -> measure("Fatia", "2000.1"));
        assertInvalid("quantity", () -> measure("Fatia", "10").toBaseUnit(BigDecimal.ZERO));
    }
}
