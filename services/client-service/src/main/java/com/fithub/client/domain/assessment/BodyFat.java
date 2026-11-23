package com.fithub.client.domain.assessment;

import java.math.BigDecimal;

/**
 * % de gordura da avaliação, um por origem (FR-19): os métodos não são comparáveis entre si, então cada valor sai
 * marcado. Calculados uma vez, na gravação, e não recalculados depois.
 *
 * @param pollock3 Jackson & Pollock, 3 dobras (DEV-0045)
 * @param pollock7 Jackson & Pollock, 7 dobras (DEV-0045)
 * @param navy fórmula da Marinha dos EUA, na avaliação online (DEV-0046)
 * @param navyHeightCm altura do perfil usada na fórmula da Marinha, gravada junto
 * @param bioimpedance % informado de uma bioimpedância, sem cálculo (DEV-0047)
 */
public record BodyFat(
        BigDecimal pollock3, BigDecimal pollock7, BigDecimal navy, Integer navyHeightCm, BigDecimal bioimpedance) {

    public static final BigDecimal MIN_BIOIMPEDANCE = new BigDecimal("3");
    public static final BigDecimal MAX_BIOIMPEDANCE = new BigDecimal("70");
    public static final BodyFat NONE = new BodyFat(null, null, null, null, null);

    public BodyFat {
        Measures.requireInRangeIfPresent(
                "bioimpedanceBodyFatPercent",
                bioimpedance,
                MIN_BIOIMPEDANCE,
                MAX_BIOIMPEDANCE,
                "% de gordura da bioimpedância de 3 a 70, com até 1 casa decimal.");
    }
}
