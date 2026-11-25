package com.fithub.account.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O estado da assinatura sai das datas (DEV-0099). */
class SubscriptionTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID PROFESSIONAL = UUID.randomUUID();

    @Test
    void cadastroNasceEmTesteAteOFimDoPrazo() {
        Subscription subscription = Subscription.startTrial(PROFESSIONAL, NOW, Duration.ofDays(14));
        assertThat(subscription.plan()).isEqualTo(Subscription.Plan.PROFESSIONAL_MONTHLY);
        assertThat(subscription.trialEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(subscription.paidUntil()).isNull();
        assertThat(subscription.status(NOW)).isEqualTo(Subscription.Status.TRIAL);
        assertThat(subscription.status(NOW.plus(Duration.ofDays(14)).minusSeconds(1)))
                .isEqualTo(Subscription.Status.TRIAL);
        // No instante exato do fim, o teste já acabou.
        assertThat(subscription.status(NOW.plus(Duration.ofDays(14)))).isEqualTo(Subscription.Status.EXPIRED);
        assertThat(subscription.validUntil()).isEqualTo(subscription.trialEndsAt());
        assertThatThrownBy(() -> Subscription.startTrial(PROFESSIONAL, NOW, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Subscription.startTrial(PROFESSIONAL, NOW, Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void periodoPagoDeixaAtivaAteAcabarEValeMaisQueOTeste() {
        Subscription trial = Subscription.startTrial(PROFESSIONAL, NOW, Duration.ofDays(14));
        Instant paid = NOW.plus(Duration.ofDays(30));
        Subscription active = trial.activateUntil(paid);
        assertThat(active.status(NOW)).isEqualTo(Subscription.Status.ACTIVE);
        assertThat(active.status(paid.minusSeconds(1))).isEqualTo(Subscription.Status.ACTIVE);
        assertThat(active.status(paid)).isEqualTo(Subscription.Status.EXPIRED);
        assertThat(active.validUntil()).isEqualTo(paid);

        // Pago que acabou antes do fim do teste: volta a valer o teste.
        Subscription shortPaid = trial.activateUntil(NOW.plus(Duration.ofDays(2)));
        assertThat(shortPaid.status(NOW.plus(Duration.ofDays(3)))).isEqualTo(Subscription.Status.TRIAL);
        assertThat(shortPaid.validUntil()).isEqualTo(trial.trialEndsAt());
    }

    @Test
    void estenderOPagoNuncaEncurta() {
        Instant later = NOW.plus(Duration.ofDays(60));
        Subscription subscription =
                Subscription.startTrial(PROFESSIONAL, NOW, Duration.ofDays(14)).activateUntil(later);
        assertThat(subscription.activateUntil(NOW.plus(Duration.ofDays(30))).paidUntil())
                .isEqualTo(later);
        assertThat(subscription.activateUntil(later.plus(Duration.ofDays(30))).paidUntil())
                .isEqualTo(later.plus(Duration.ofDays(30)));
    }
}
