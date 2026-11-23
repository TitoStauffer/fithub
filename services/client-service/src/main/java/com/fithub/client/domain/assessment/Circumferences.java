package com.fithub.client.domain.assessment;

import java.math.BigDecimal;

/** Circunferências em cm (FR-14), todas opcionais, de 10 a 300 com até 1 casa decimal. */
public record Circumferences(
        BigDecimal neck,
        BigDecimal chest,
        BigDecimal waist,
        BigDecimal abdomen,
        BigDecimal hip,
        BigDecimal arm,
        BigDecimal forearm,
        BigDecimal thigh,
        BigDecimal calf) {

    public static final BigDecimal MIN = new BigDecimal("10");
    public static final BigDecimal MAX = new BigDecimal("300");
    public static final Circumferences NONE = new Circumferences(null, null, null, null, null, null, null, null, null);

    private static final String MESSAGE = "Circunferência em cm, de 10 a 300, com até 1 casa decimal.";

    public Circumferences {
        check("circumferences.neck", neck);
        check("circumferences.chest", chest);
        check("circumferences.waist", waist);
        check("circumferences.abdomen", abdomen);
        check("circumferences.hip", hip);
        check("circumferences.arm", arm);
        check("circumferences.forearm", forearm);
        check("circumferences.thigh", thigh);
        check("circumferences.calf", calf);
    }

    private static void check(String field, BigDecimal value) {
        Measures.requireInRangeIfPresent(field, value, MIN, MAX, MESSAGE);
    }
}
