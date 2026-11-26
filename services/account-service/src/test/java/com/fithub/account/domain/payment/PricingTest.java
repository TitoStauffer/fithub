package com.fithub.account.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Preço do plano mensal e o de lançamento (DEV-0098, DEV-0100). */
class PricingTest {

    private static final LocalDate CUTOFF = LocalDate.of(2027, 6, 30);
    private final Pricing pricing = new Pricing(new BigDecimal("200.00"), new BigDecimal("150.00"), 3, CUTOFF);

    @Test
    void untilTheCutoffDayTheOfferIsTheLaunchPrice() {
        assertThat(pricing.offerOn(CUTOFF)).isEqualTo(new Pricing.Offer(new BigDecimal("150.00"), true));
        assertThat(pricing.offerOn(CUTOFF.minusDays(10)).launch()).isTrue();
    }

    @Test
    void afterTheCutoffDayTheOfferIsTheFullPrice() {
        assertThat(pricing.offerOn(CUTOFF.plusDays(1))).isEqualTo(new Pricing.Offer(new BigDecimal("200.00"), false));
    }

    @Test
    void invalidPricesAreRefused() {
        assertThatThrownBy(() -> new Pricing(BigDecimal.ZERO, new BigDecimal("150"), 3, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pricing(new BigDecimal("200"), BigDecimal.ZERO, 3, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pricing(new BigDecimal("200"), new BigDecimal("250"), 3, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pricing(new BigDecimal("200"), new BigDecimal("150"), 0, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
