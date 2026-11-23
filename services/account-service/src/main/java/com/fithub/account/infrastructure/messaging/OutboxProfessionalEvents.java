package com.fithub.account.infrastructure.messaging;

import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalEvents;
import com.fithub.commons.messaging.OutboxWriter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Grava o {@code ProfissionalCadastrado} no outbox (DEV-0030): routing key
 * {@code account.professional-registered.v1}, na exchange do serviço ({@code fithub.account}). O payload
 * segue o contrato de {@code libs/event-contracts} e leva só o necessário para a projeção dos outros
 * serviços. O id do profissional é o {@code sub} da conta (ADR-009); o registro no conselho (CREF/CRN) não sai
 * do account-service.
 */
@Component
class OutboxProfessionalEvents implements ProfessionalEvents {

    static final String REGISTERED = "account.professional-registered.v1";

    private final OutboxWriter outbox;

    OutboxProfessionalEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** Payload v1. Mudança incompatível gera uma v2, nunca altera esta (AGENTS.md). */
    record Registered(UUID professionalId, String professionalType, String name, Instant occurredAt) {}

    @Override
    public void registered(Professional professional) {
        outbox.append(
                REGISTERED,
                new Registered(
                        professional.id(), typeOf(professional.type()), professional.name(), professional.createdAt()));
    }

    /** Valores do contrato v1 para o tipo de profissional. */
    static String typeOf(Professional.Type type) {
        return switch (type) {
            case PERSONAL -> "personal_trainer";
            case NUTRITIONIST -> "nutritionist";
        };
    }
}
