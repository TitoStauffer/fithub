package com.fithub.client.domain.assessment;

import java.math.BigDecimal;

/**
 * % de gordura da avaliação, um por origem (FR-19): os métodos não são comparáveis entre si, então cada valor sai
 * marcado. Calculados uma vez, na gravação, e não recalculados depois.
 *
 * @param pollock3 Jackson & Pollock, 3 dobras (DEV-0045)
 * @param pollock7 Jackson & Pollock, 7 dobras (DEV-0045)
 */
public record BodyFat(BigDecimal pollock3, BigDecimal pollock7) {

    public static final BodyFat NONE = new BodyFat(null, null);
}
