package com.fithub.client.infrastructure.messaging;

import com.fithub.client.domain.profile.ClientEvents;
import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.commons.messaging.OutboxWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Grava o {@code ClienteAtualizado} no outbox (DEV-0034): routing key {@code client.client-updated.v1}, na
 * exchange do serviço ({@code fithub.client}). O payload segue o contrato de {@code libs/event-contracts} e
 * leva só os dados básicos: saúde, objetivo e altura nunca saem do client-service por este evento.
 */
@Component
class OutboxClientEvents implements ClientEvents {

    static final String UPDATED = "client.client-updated.v1";

    private final OutboxWriter outbox;

    OutboxClientEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** Payload v1. Mudança incompatível gera uma v2, nunca altera esta (AGENTS.md). */
    record Updated(UUID clientId, String name, LocalDate birthDate, String gender, Instant occurredAt) {}

    @Override
    public void updated(ClientProfile profile) {
        // O id do cliente é a conta do Keycloak (o sub do token, sempre um UUID).
        outbox.append(
                UPDATED,
                new Updated(
                        UUID.fromString(profile.accountId()),
                        profile.name(),
                        profile.birthDate(),
                        profile.gender().name(),
                        profile.updatedAt()));
    }
}
