package com.fithub.nutrition.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Alimento da refeição (DEV-0081, FR-43): o alimento e a quantidade na unidade base dele (g ou ml). A medida caseira
 * (ex.: 2 colheres) é opcional e só serve para exibir; quando vem, a quantidade é calculada dela.
 *
 * @param measureId a medida caseira da nutricionista usada para exibir, ou nada
 * @param measureCount quantas medidas (de 0,1 a 100), junto com a medida
 * @param alternatives até 2 trocas possíveis (DEV-0082, FR-44): a secundária e a terciária, na ordem
 */
public record MealItem(
        UUID foodId, BigDecimal amount, UUID measureId, BigDecimal measureCount, List<Alternative> alternatives) {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.1");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("5000");
    public static final BigDecimal MIN_MEASURE_COUNT = new BigDecimal("0.1");
    public static final BigDecimal MAX_MEASURE_COUNT = new BigDecimal("100");
    public static final int MAX_ALTERNATIVES = 2;

    /** Troca possível do alimento: outro alimento, na quantidade dele (0,1 a 5000 na unidade base). */
    public record Alternative(UUID foodId, BigDecimal amount) {

        public Alternative {
            if (foodId == null) {
                throw new InvalidMealPlanException("foodId", "Escolha o alimento da alternativa.");
            }
            amount = checkedAmount(amount);
        }
    }

    /** O item sem alternativas. */
    public MealItem(UUID foodId, BigDecimal amount, UUID measureId, BigDecimal measureCount) {
        this(foodId, amount, measureId, measureCount, List.of());
    }

    public MealItem {
        if (foodId == null) {
            throw new InvalidMealPlanException("foodId", "Escolha o alimento.");
        }
        if ((measureId == null) != (measureCount == null)) {
            throw new InvalidMealPlanException("measureCount", "Informe a medida e quantas, ou nenhuma das duas.");
        }
        if (measureCount != null) {
            if (!validMeasureCount(measureCount)) {
                throw new InvalidMealPlanException("measureCount", "A quantidade de medidas vai de 0,1 a 100.");
            }
            measureCount = measureCount.setScale(1, RoundingMode.HALF_UP);
        }
        // A medida vem antes: com ela, a quantidade é calculada e o erro dela é o que a tela precisa mostrar.
        amount = checkedAmount(amount);
        alternatives = checkedAlternatives(foodId, alternatives);
    }

    private static BigDecimal checkedAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidMealPlanException("amount", "A quantidade vai de 0,1 a 5000 g (ou ml).");
        }
        return amount.setScale(1, RoundingMode.HALF_UP);
    }

    /** Até 2, sem repetir o alimento principal nem a outra alternativa. */
    private static List<Alternative> checkedAlternatives(UUID foodId, List<Alternative> alternatives) {
        if (alternatives == null) {
            return List.of();
        }
        if (alternatives.size() > MAX_ALTERNATIVES) {
            throw new InvalidMealPlanException("alternatives", "Cada alimento tem até 2 alternativas.");
        }
        Set<UUID> seen = new HashSet<>();
        seen.add(foodId);
        for (int index = 0; index < alternatives.size(); index++) {
            Alternative alternative = alternatives.get(index);
            if (alternative == null) {
                throw new InvalidMealPlanException("alternatives[" + index + "].foodId", "Escolha o alimento.");
            }
            if (!seen.add(alternative.foodId())) {
                throw new InvalidMealPlanException(
                        "alternatives[" + index + "].foodId",
                        "A alternativa precisa ser outro alimento: não repete o principal nem a outra alternativa.");
            }
        }
        return List.copyOf(alternatives);
    }

    /** Se a quantidade de medidas está na faixa: só então dá para calcular a quantidade a partir dela. */
    public static boolean validMeasureCount(BigDecimal measureCount) {
        return measureCount != null
                && measureCount.compareTo(MIN_MEASURE_COUNT) >= 0
                && measureCount.compareTo(MAX_MEASURE_COUNT) <= 0;
    }
}
