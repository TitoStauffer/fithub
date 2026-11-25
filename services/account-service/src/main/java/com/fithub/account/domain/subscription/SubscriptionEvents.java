package com.fithub.account.domain.subscription;

import java.time.Instant;

/**
 * Eventos da assinatura para os outros serviços (porta do domínio, ADR-003). Chamada dentro da transação que mudou a
 * assinatura: se ela for desfeita, o evento também é.
 */
public interface SubscriptionEvents {

    /** A assinatura começou ou mudou agora; os serviços de prescrição passam a seguir o {@code validUntil} dela. */
    void changed(Subscription subscription, Instant now);
}
