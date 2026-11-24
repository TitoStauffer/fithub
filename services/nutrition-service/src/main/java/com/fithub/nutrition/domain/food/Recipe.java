package com.fithub.nutrition.domain.food;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Receita da nutricionista (DEV-0077, FR-36): ingredientes com a quantidade e o rendimento total. Os valores por 100 g
 * da receita saem dos ingredientes, calculados ao salvar.
 *
 * @param yieldGrams rendimento total, em gramas (de 1 a 20000)
 */
public record Recipe(List<Ingredient> ingredients, BigDecimal yieldGrams) {

    public static final int MAX_INGREDIENTS = 30;
    public static final BigDecimal MIN_YIELD = BigDecimal.ONE;
    public static final BigDecimal MAX_YIELD = new BigDecimal("20000");

    /**
     * Um ingrediente: o alimento e a quantidade na unidade base dele (de 0,1 a 5000).
     *
     * @param foodId o alimento usado; precisa ser visível para a dona e não pode ser outra receita
     */
    public record Ingredient(UUID foodId, BigDecimal amount) {

        public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.1");
        public static final BigDecimal MAX_AMOUNT = new BigDecimal("5000");
    }

    public Recipe {
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS) {
            throw new InvalidFoodException("recipe.ingredients", "A receita tem de 1 a 30 ingredientes.");
        }
        for (int index = 0; index < ingredients.size(); index++) {
            requireValid(ingredients.get(index), "recipe.ingredients[" + index + "]");
        }
        if (yieldGrams == null || yieldGrams.compareTo(MIN_YIELD) < 0 || yieldGrams.compareTo(MAX_YIELD) > 0) {
            throw new InvalidFoodException("recipe.yieldGrams", "O rendimento vai de 1 a 20000 g.");
        }
        ingredients = ingredients.stream()
                .map(each -> new Ingredient(each.foodId(), each.amount().setScale(1, RoundingMode.HALF_UP)))
                .toList();
        yieldGrams = yieldGrams.setScale(1, RoundingMode.HALF_UP);
    }

    private static void requireValid(Ingredient ingredient, String path) {
        if (ingredient == null || ingredient.foodId() == null) {
            throw new InvalidFoodException(path + ".foodId", "Escolha o alimento do ingrediente.");
        }
        if (ingredient.amount() == null
                || ingredient.amount().compareTo(Ingredient.MIN_AMOUNT) < 0
                || ingredient.amount().compareTo(Ingredient.MAX_AMOUNT) > 0) {
            throw new InvalidFoodException(path + ".amount", "A quantidade vai de 0,1 a 5000 g (ou ml).");
        }
    }

    /**
     * Valores por 100 g da receita: a soma do que cada ingrediente traz (valor por 100 × quantidade / 100), dividida pelo
     * rendimento e multiplicada por 100. A fibra falta se algum ingrediente não a traz.
     *
     * @param foods os alimentos dos ingredientes, pelo id
     */
    public Food.Nutrients per100(Map<UUID, Food> foods) {
        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal fiber = BigDecimal.ZERO;
        boolean fiberKnown = true;
        for (Ingredient ingredient : ingredients) {
            Food.Nutrients values = foods.get(ingredient.foodId()).per100();
            energy = energy.add(values.energyKcal().multiply(ingredient.amount()));
            protein = protein.add(values.proteinG().multiply(ingredient.amount()));
            carbs = carbs.add(values.carbsG().multiply(ingredient.amount()));
            fat = fat.add(values.fatG().multiply(ingredient.amount()));
            if (values.fiberG() == null) {
                fiberKnown = false;
            } else {
                fiber = fiber.add(values.fiberG().multiply(ingredient.amount()));
            }
        }
        return new Food.Nutrients(
                perHundredOfYield(energy),
                perHundredOfYield(protein),
                perHundredOfYield(carbs),
                perHundredOfYield(fat),
                fiberKnown ? perHundredOfYield(fiber) : null);
    }

    /** A receita é vegana só se todos os ingredientes forem. */
    public boolean vegan(Map<UUID, Food> foods) {
        return ingredients.stream()
                .allMatch(ingredient -> foods.get(ingredient.foodId()).vegan());
    }

    /** Soma de (valor por 100 × quantidade) dividida pelo rendimento: o valor por 100 g da receita. */
    private BigDecimal perHundredOfYield(BigDecimal sumOfValueTimesAmount) {
        return sumOfValueTimesAmount.divide(yieldGrams, 4, RoundingMode.HALF_UP);
    }
}
