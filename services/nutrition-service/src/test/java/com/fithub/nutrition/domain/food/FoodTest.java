package com.fithub.nutrition.domain.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do alimento: valores por 100, dona e origem (DEV-0074). */
class FoodTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID NUTRI = UUID.randomUUID();

    private static Food.Nutrients per100(String kcal, String protein, String carbs, String fat, String fiber) {
        return new Food.Nutrients(
                kcal == null ? null : new BigDecimal(kcal),
                protein == null ? null : new BigDecimal(protein),
                carbs == null ? null : new BigDecimal(carbs),
                fat == null ? null : new BigDecimal(fat),
                fiber == null ? null : new BigDecimal(fiber));
    }

    private static Food own(String name, Food.Nutrients nutrients) {
        return new Food(
                UUID.randomUUID(), Food.Kind.OWN, NUTRI, name, Food.BaseUnit.G, nutrients, true, null, NOW, NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidFoodException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void valoresPor100DentroDasFaixas() {
        Food.Nutrients rice = per100("128.26", "2.5", "28.1", "0.2", null);
        assertThat(rice.energyKcal()).isEqualByComparingTo("128.3");
        assertThat(rice.fiberG()).isNull();
        assertThat(per100("900", "0", "0", "100", "0").fatG()).isEqualByComparingTo("100");
        // Os três somam exatamente 100: vale.
        assertThat(per100("400", "30", "40", "30", null).carbsG()).isEqualByComparingTo("40");

        assertInvalid("energyKcal", () -> per100(null, "1", "1", "1", null));
        assertInvalid("energyKcal", () -> per100("-1", "1", "1", "1", null));
        assertInvalid("energyKcal", () -> per100("900.1", "1", "1", "1", null));
        assertInvalid("proteinG", () -> per100("100", "100.01", "0", "0", null));
        assertInvalid("carbsG", () -> per100("100", "0", "-0.1", "0", null));
        assertInvalid("fatG", () -> per100("100", "0", "0", null, null));
        assertInvalid("fiberG", () -> per100("100", "0", "0", "0", "101"));
        assertInvalid("per100", () -> per100("400", "40", "40", "20.01", null));
    }

    @Test
    void nomeDonaEOrigem() {
        Food.Nutrients nutrients = per100("100", "1", "1", "1", null);
        assertThat(own("  Pão de fermentação natural  ", nutrients).name()).isEqualTo("Pão de fermentação natural");
        assertInvalid("name", () -> own("a", nutrients));
        assertInvalid("name", () -> own("a".repeat(121), nutrients));
        // Próprio sem dona, TACO com dona, TACO sem fonte e próprio com fonte são recusados.
        assertInvalid(
                "ownerId",
                () -> new Food(
                        UUID.randomUUID(),
                        Food.Kind.OWN,
                        null,
                        "Arroz",
                        Food.BaseUnit.G,
                        nutrients,
                        true,
                        null,
                        NOW,
                        NOW));
        assertInvalid(
                "ownerId",
                () -> new Food(
                        UUID.randomUUID(),
                        Food.Kind.TACO,
                        NUTRI,
                        "Arroz",
                        Food.BaseUnit.G,
                        nutrients,
                        true,
                        new Food.Source("TACO", "1"),
                        NOW,
                        NOW));
        assertInvalid(
                "source",
                () -> new Food(
                        UUID.randomUUID(),
                        Food.Kind.TACO,
                        null,
                        "Arroz",
                        Food.BaseUnit.G,
                        nutrients,
                        true,
                        null,
                        NOW,
                        NOW));
        assertInvalid(
                "source",
                () -> new Food(
                        UUID.randomUUID(),
                        Food.Kind.OWN,
                        NUTRI,
                        "Arroz",
                        Food.BaseUnit.G,
                        nutrients,
                        true,
                        new Food.Source("TACO", "1"),
                        NOW,
                        NOW));
        assertInvalid(
                "baseUnit",
                () -> new Food(
                        UUID.randomUUID(), Food.Kind.OWN, NUTRI, "Arroz", null, nutrients, true, null, NOW, NOW));
    }

    @Test
    void oProprioSoEVistoPelaDona() {
        Food mine = own("Granola da casa", per100("400", "10", "60", "15", "8"));
        assertThat(mine.visibleTo(NUTRI)).isTrue();
        assertThat(mine.visibleTo(UUID.randomUUID())).isFalse();
        assertThat(mine.visibleTo(null)).isFalse();
    }
}
