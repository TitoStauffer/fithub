package com.fithub.nutrition.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Alimento da refeição (DEV-0081, FR-43): o alimento e a quantidade na unidade base dele (g ou ml). A medida caseira
 * (ex.: 2 colheres) é opcional e só serve para exibir; quando vem, a quantidade é calculada dela.
 *
 * @param measureId a medida caseira da nutricionista usada para exibir, ou nada
 * @param measureCount quantas medidas (de 0,1 a 100), junto com a medida
 */
public record MealItem(java.util.UUID foodId, BigDecimal amount, java.util.UUID measureId, BigDecimal measureCount) {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.1");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("5000");
    public static final BigDecimal MIN_MEASURE_COUNT = new BigDecimal("0.1");
    public static final BigDecimal MAX_MEASURE_COUNT = new BigDecimal("100");

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
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidMealPlanException("amount", "A quantidade vai de 0,1 a 5000 g (ou ml).");
        }
        amount = amount.setScale(1, RoundingMode.HALF_UP);
    }

    /** Se a quantidade de medidas está na faixa: só então dá para calcular a quantidade a partir dela. */
    public static boolean validMeasureCount(BigDecimal measureCount) {
        return measureCount != null
                && measureCount.compareTo(MIN_MEASURE_COUNT) >= 0
                && measureCount.compareTo(MAX_MEASURE_COUNT) <= 0;
    }
}
