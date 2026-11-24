package com.fithub.nutrition.domain.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Receita: valores por 100 g calculados dos ingredientes e etiqueta vegano (DEV-0077). */
class RecipeTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static Food food(String protein, String fiber, boolean vegan) {
        return new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                "Ingrediente",
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("100"),
                        new BigDecimal(protein),
                        new BigDecimal("20"),
                        new BigDecimal("1"),
                        fiber == null ? null : new BigDecimal(fiber)),
                vegan,
                new Food.Source("TACO", UUID.randomUUID().toString()),
                NOW,
                NOW);
    }

    private static Recipe.Ingredient use(Food food, String amount) {
        return new Recipe.Ingredient(food.id(), new BigDecimal(amount));
    }

    @Test
    void arrozEFeijaoRendendo300GTem33GDeProteinaPor100G() {
        Food rice = food("2.5", "1.6", true);
        Food beans = food("4.8", "8.5", true);
        Recipe recipe = new Recipe(List.of(use(rice, "200"), use(beans, "100")), new BigDecimal("300"));
        Map<UUID, Food> foods = Map.of(rice.id(), rice, beans.id(), beans);

        Food.Nutrients per100 = recipe.per100(foods);
        // (2,5 × 200 + 4,8 × 100) / 300 = 3,27 g por 100 g.
        assertThat(per100.proteinG()).isEqualByComparingTo("3.27");
        assertThat(per100.energyKcal()).isEqualByComparingTo("100");
        // (1,6 × 200 + 8,5 × 100) / 300 = 3,9.
        assertThat(per100.fiberG()).isEqualByComparingTo("3.9");
        assertThat(recipe.vegan(foods)).isTrue();
    }

    @Test
    void fibraFaltandoNumIngredienteEIngredienteNaoVegano() {
        Food rice = food("2.5", null, true);
        Food egg = food("13", "0", false);
        Recipe recipe = new Recipe(List.of(use(rice, "200"), use(egg, "50")), new BigDecimal("250"));
        Map<UUID, Food> foods = Map.of(rice.id(), rice, egg.id(), egg);

        assertThat(recipe.per100(foods).fiberG()).isNull();
        assertThat(recipe.vegan(foods)).isFalse();
    }

    @Test
    void ingredientesERendimentoDentroDasFaixas() {
        Food rice = food("2.5", null, true);
        assertThatThrownBy(() -> new Recipe(List.of(), BigDecimal.TEN))
                .isInstanceOfSatisfying(
                        InvalidFoodException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("recipe.ingredients"));
        assertThatThrownBy(() -> new Recipe(List.of(use(rice, "0.05")), BigDecimal.TEN))
                .isInstanceOfSatisfying(
                        InvalidFoodException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("recipe.ingredients[0].amount"));
        assertThatThrownBy(() -> new Recipe(List.of(use(rice, "100")), new BigDecimal("0.5")))
                .isInstanceOfSatisfying(
                        InvalidFoodException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("recipe.yieldGrams"));
    }
}
