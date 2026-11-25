package com.fithub.account.infrastructure.messaging;

import com.fithub.account.domain.subscription.Subscription;
import com.fithub.account.domain.subscription.SubscriptionEvents;
import com.fithub.commons.messaging.OutboxWriter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Grava o {@code AssinaturaAlterada} no outbox (DEV-0101): routing key {@code account.subscription-changed.v1}. O
 * {@code validUntil} deixa cada serviço bloquear sozinho quando o teste ou o período pago acaba, sem rotina agendada
 * aqui para avisar.
 */
@Component
class OutboxSubscriptionEvents implements SubscriptionEvents {

    static final String CHANGED = "account.subscription-changed.v1";

    private final OutboxWriter outbox;

    OutboxSubscriptionEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** Payload v1, com o {@code validUntil} opcional acrescentado de forma compatível. */
    record Changed(UUID professionalId, boolean active, Instant occurredAt, Instant validUntil) {}

    @Override
    public void changed(Subscription subscription, Instant now) {
        outbox.append(
                CHANGED,
                new Changed(
                        subscription.professionalId(),
                        subscription.status(now) != Subscription.Status.EXPIRED,
                        now,
                        subscription.validUntil()));
    }
}
