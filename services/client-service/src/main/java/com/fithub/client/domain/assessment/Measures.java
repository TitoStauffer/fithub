package com.fithub.client.domain.assessment;

import java.math.BigDecimal;

/** Regras comuns às medidas da avaliação: faixa e no máximo uma casa decimal. */
final class Measures {

    private Measures() {}

    /** Exige o valor dentro da faixa, com até 1 casa decimal. */
    static void requireInRange(String field, BigDecimal value, BigDecimal min, BigDecimal max, String message) {
        if (value == null || !inRange(value, min, max)) {
            throw new InvalidAssessmentException(field, message);
        }
    }

    /** Valor opcional: vazio passa; preenchido segue a mesma regra. */
    static void requireInRangeIfPresent(
            String field, BigDecimal value, BigDecimal min, BigDecimal max, String message) {
        if (value != null && !inRange(value, min, max)) {
            throw new InvalidAssessmentException(field, message);
        }
    }

    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.stripTrailingZeros().scale() <= 1 && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
