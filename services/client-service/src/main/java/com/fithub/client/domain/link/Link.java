package com.fithub.client.domain.link;

import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Vínculo entre cliente e profissional (DEV-0036 e DEV-0037, FR-10 a FR-13). O tipo é o do profissional; o
 * cliente tem no máximo um vínculo ativo de cada tipo (BR-07). Qualquer das partes encerra; o encerramento é
 * imediato e definitivo (BR-05).
 *
 * @param clientId o {@code sub} da conta do cliente (ADR-009)
 * @param professionalId o {@code sub} da conta do profissional (ADR-009)
 * @param invitationId o convite aceito, ou {@code null} quando o cliente usou o código pessoal do profissional
 * @param endedAt quando foi encerrado, ou {@code null} se ativo
 * @param endedBy quem encerrou, ou {@code null} se ativo
 */
public record Link(
        UUID id,
        UUID clientId,
        UUID professionalId,
        ProfessionalType type,
        Status status,
        UUID invitationId,
        Instant startedAt,
        Instant endedAt,
        Party endedBy) {

    public enum Status {
        ACTIVE,
        ENDED
    }

    /** As partes do vínculo. */
    public enum Party {
        CLIENT,
        PROFESSIONAL
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
        if ((status == Status.ENDED) != (endedAt != null && endedBy != null)) {
            throw new IllegalArgumentException("Vínculo encerrado precisa de data e de quem encerrou.");
        }
    }

    /** Vínculo novo, ativo a partir de agora. */
    public static Link start(
            UUID id, UUID clientId, UUID professionalId, ProfessionalType type, UUID invitationId, Instant now) {
        return new Link(id, clientId, professionalId, type, Status.ACTIVE, invitationId, now, null, null);
    }

    /** A parte que esta conta é no vínculo, ou vazio se ela não for parte. */
    public Optional<Party> partyOf(UUID account) {
        if (clientId.equals(account)) {
            return Optional.of(Party.CLIENT);
        }
        if (professionalId.equals(account)) {
            return Optional.of(Party.PROFESSIONAL);
        }
        return Optional.empty();
    }

    /** Encerramento pela parte dada. Encerrar de novo não muda nada: vale o primeiro encerramento. */
    public Link end(Party by, Instant now) {
        if (status == Status.ENDED) {
            return this;
        }
        return new Link(id, clientId, professionalId, type, Status.ENDED, invitationId, startedAt, now, by);
    }
}
