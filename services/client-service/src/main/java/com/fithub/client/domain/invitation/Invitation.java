package com.fithub.client.domain.invitation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Convite do profissional ao cliente (DEV-0035, FR-10). Vale 7 dias e é de uso único; o profissional pode
 * cancelar. Token e código só existem em claro na criação: aqui ficam os hashes.
 *
 * <p>Decisão do Agent na ausência do Tito, para revisão: validade de 7 dias, código de 8 caracteres.
 *
 * @param email para onde o convite foi enviado, ou {@code null} quando o profissional compartilha o link
 */
public record Invitation(
        UUID id,
        UUID professionalId,
        String email,
        String tokenHash,
        String codeHash,
        Status status,
        Instant expiresAt,
        Instant createdAt,
        Instant cancelledAt) {

    public static final Duration VALIDITY = Duration.ofDays(7);
    public static final int MAX_EMAIL = 254;

    /** O que está gravado. Vencido não é gravado: é calculado pela data ({@link #stateAt}). */
    public enum Status {
        PENDING,
        ACCEPTED,
        CANCELLED
    }

    /** Como o convite está agora, para quem o vê. */
    public enum State {
        PENDING,
        EXPIRED,
        ACCEPTED,
        CANCELLED
    }

    public Invitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(professionalId, "professionalId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (email != null && (email.isBlank() || email.length() > MAX_EMAIL || !email.contains("@"))) {
            throw new InvalidInvitationException("email", "Informe um e-mail válido.");
        }
    }

    /** Convite novo, pendente, vencendo em {@link #VALIDITY}. O e-mail chega sem espaços nas pontas. */
    public static Invitation create(
            UUID id, UUID professionalId, String email, String tokenHash, String codeHash, Instant now) {
        String trimmed = email == null || email.isBlank() ? null : email.strip();
        return new Invitation(
                id, professionalId, trimmed, tokenHash, codeHash, Status.PENDING, now.plus(VALIDITY), now, null);
    }

    public State stateAt(Instant now) {
        return switch (status) {
            case ACCEPTED -> State.ACCEPTED;
            case CANCELLED -> State.CANCELLED;
            case PENDING -> now.isBefore(expiresAt) ? State.PENDING : State.EXPIRED;
        };
    }

    /** Se ainda pode ser aceito: pendente e dentro da validade. */
    public boolean usableAt(Instant now) {
        return stateAt(now) == State.PENDING;
    }

    /** Cancelamento pelo profissional. Cancelar de novo não muda nada; convite aceito não se cancela. */
    public Invitation cancel(Instant now) {
        if (status == Status.CANCELLED) {
            return this;
        }
        if (status == Status.ACCEPTED) {
            throw new InvitationStateException("Este convite já foi aceito; encerre o vínculo em vez de cancelar.");
        }
        return new Invitation(
                id, professionalId, email, tokenHash, codeHash, Status.CANCELLED, expiresAt, createdAt, now);
    }
}
