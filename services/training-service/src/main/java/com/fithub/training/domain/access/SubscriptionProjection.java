package com.fithub.training.domain.access;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Projeção do {@code AssinaturaAlterada} do account-service (porta do domínio, DEV-0101). */
public interface SubscriptionProjection {

    /** Guarda o estado do evento; um evento mais antigo que o guardado não muda nada (chegada fora de ordem). */
    void update(UUID professionalId, SubscriptionState state, Instant occurredAt);

    Optional<SubscriptionState> find(UUID professionalId);
}
