package com.fithub.client.domain.link;

import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo entre cliente e profissional (DEV-0036, FR-10 a FR-13). O tipo é o do profissional; o cliente tem no
 * máximo um vínculo ativo de cada tipo (BR-07). O encerramento chega na DEV-0037.
 *
 * @param clientId o {@code sub} da conta do cliente (ADR-009)
 * @param professionalId o {@code sub} da conta do profissional (ADR-009)
 * @param invitationId o convite aceito, ou {@code null} quando o cliente usou o código pessoal do profissional
 */
public record Link(
        UUID id,
        UUID clientId,
        UUID professionalId,
        ProfessionalType type,
        Status status,
        UUID invitationId,
        Instant startedAt) {

    public enum Status {
        ACTIVE,
        ENDED
    }

    public Link {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(professionalId, "professionalId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        if (clientId.equals(professionalId)) {
            throw new LinkRuleException("Você não pode se vincular a si mesmo.");
        }
    }

    /** Vínculo novo, ativo a partir de agora. */
    public static Link start(
            UUID id, UUID clientId, UUID professionalId, ProfessionalType type, UUID invitationId, Instant now) {
        return new Link(id, clientId, professionalId, type, Status.ACTIVE, invitationId, now);
    }
}
