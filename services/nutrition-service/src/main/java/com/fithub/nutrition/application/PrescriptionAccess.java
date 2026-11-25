package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.SubscriptionProjection;
import com.fithub.nutrition.domain.access.SubscriptionRequiredException;
import com.fithub.nutrition.domain.access.SubscriptionState;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Quem pode criar e editar prescrições (DEV-0101, FR-06): o profissional com assinatura válida. Sem evento recebido
 * do profissional, não bloqueia (só cadastros de desenvolvimento anteriores à assinatura ficam assim).
 */
@Service
public class PrescriptionAccess {

    private final SubscriptionProjection subscriptions;
    private final Clock clock;

    public PrescriptionAccess(SubscriptionProjection subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    /** Lança {@link SubscriptionRequiredException} se a assinatura do profissional venceu. */
    public void requireSubscription(String professionalAccount) {
        Instant now = clock.instant();
        boolean allowed = subscriptions
                .find(UUID.fromString(professionalAccount))
                .map(state -> state.allowsPrescribingAt(now))
                .orElse(true);
        if (!allowed) {
            throw new SubscriptionRequiredException();
        }
    }

    /** Projeta o {@code AssinaturaAlterada}. */
    public void subscriptionChanged(UUID professionalId, boolean active, Instant validUntil, Instant occurredAt) {
        subscriptions.update(professionalId, new SubscriptionState(active, validUntil), occurredAt);
    }
}
