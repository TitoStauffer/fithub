package com.fithub.nutrition.domain.access;

import java.time.Instant;

/**
 * A assinatura do profissional como chegou no último {@code AssinaturaAlterada} (DEV-0101, FR-06). Com
 * {@code validUntil}, o bloqueio começa nesse instante sem esperar outro evento; sem ele, vale o {@code active}.
 */
public record SubscriptionState(boolean active, Instant validUntil) {

    public boolean allowsPrescribingAt(Instant now) {
        return validUntil != null ? now.isBefore(validUntil) : active;
    }
}
