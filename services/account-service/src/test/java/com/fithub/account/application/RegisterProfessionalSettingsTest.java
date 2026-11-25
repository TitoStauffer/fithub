package com.fithub.account.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.Test;

/** O teste grátis configurado com zero ou menos dias falha na subida (Code Review do PR #285). */
class RegisterProfessionalSettingsTest {

    private static RegisterProfessional withTrialDays(int days) {
        return new RegisterProfessional(null, null, null, null, Clock.systemUTC(), null, days);
    }

    @Test
    void testeGratisPrecisaDePeloMenosUmDia() {
        assertThatThrownBy(() -> withTrialDays(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fithub.subscription.trial-days");
        assertThatThrownBy(() -> withTrialDays(-3)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> withTrialDays(1)).doesNotThrowAnyException();
    }
}
