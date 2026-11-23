package com.fithub.client.domain.assessment;

import java.math.BigDecimal;
import java.util.stream.Stream;

/** Dobras cutâneas em mm (DEV-0045, FR-16), todas opcionais, de 1 a 80 com até 1 casa decimal. */
public record Skinfolds(
        BigDecimal chest,
        BigDecimal midaxillary,
        BigDecimal triceps,
        BigDecimal subscapular,
        BigDecimal abdomen,
        BigDecimal suprailiac,
        BigDecimal thigh) {

    public static final BigDecimal MIN = new BigDecimal("1");
    public static final BigDecimal MAX = new BigDecimal("80");
    public static final Skinfolds NONE = new Skinfolds(null, null, null, null, null, null, null);

    private static final String MESSAGE = "Dobra cutânea em mm, de 1 a 80, com até 1 casa decimal.";

    public Skinfolds {
        check("skinfolds.chest", chest);
        check("skinfolds.midaxillary", midaxillary);
        check("skinfolds.triceps", triceps);
        check("skinfolds.subscapular", subscapular);
        check("skinfolds.abdomen", abdomen);
        check("skinfolds.suprailiac", suprailiac);
        check("skinfolds.thigh", thigh);
    }

    /** Se alguma dobra foi informada. */
    public boolean any() {
        return Stream.of(chest, midaxillary, triceps, subscapular, abdomen, suprailiac, thigh)
                .anyMatch(value -> value != null);
    }

    private static void check(String field, BigDecimal value) {
        Measures.requireInRangeIfPresent(field, value, MIN, MAX, MESSAGE);
    }
}
