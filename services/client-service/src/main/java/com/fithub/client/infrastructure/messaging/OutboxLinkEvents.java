package com.fithub.client.infrastructure.messaging;

import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkEvents;
import com.fithub.commons.messaging.OutboxWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Grava {@code VinculoAtivado} e {@code VinculoEncerrado} no outbox (DEV-0038), na exchange do serviço
 * ({@code fithub.client}). Os payloads seguem os contratos de {@code libs/event-contracts}: ids, tipo, escopo e
 * datas; nenhum dado do perfil ou da saúde.
 */
@Component
class OutboxLinkEvents implements LinkEvents {

    static final String ACTIVATED = "client.link-activated.v1";
    static final String ENDED = "client.link-ended.v1";

    private final OutboxWriter outbox;

    OutboxLinkEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** Payload v1 do VinculoAtivado. */
    record Activated(
            UUID linkId,
            UUID clientId,
            UUID professionalId,
            String professionalType,
            List<String> consentScope,
            Instant occurredAt) {}

    /** Payload v1 do VinculoEncerrado. */
    record Ended(
            UUID linkId,
            UUID clientId,
            UUID professionalId,
            String professionalType,
            String endedBy,
            Instant occurredAt) {}

    @Override
    public void activated(Link link, Set<SharedData> consentScope) {
        outbox.append(
                ACTIVATED,
                new Activated(
                        link.id(),
                        link.clientId(),
                        link.professionalId(),
                        link.type().contractValue(),
                        consentScope.stream().map(OutboxLinkEvents::scopeOf).toList(),
                        link.startedAt()));
    }

    @Override
    public void ended(Link link) {
        outbox.append(
                ENDED,
                new Ended(
                        link.id(),
                        link.clientId(),
                        link.professionalId(),
                        link.type().contractValue(),
                        link.endedBy() == Link.Party.CLIENT ? "client" : "professional",
                        link.endedAt()));
    }

    /** Valores do contrato v1 para o escopo do consentimento. */
    static String scopeOf(SharedData data) {
        return switch (data) {
            case PROFILE -> "profile";
            case HEALTH -> "health_profile";
            case ASSESSMENTS -> "body_assessments";
            case PHOTOS -> "photos";
        };
    }
}
