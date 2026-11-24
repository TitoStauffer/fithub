package com.fithub.training.domain.restriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Restrição de mobilidade do aluno (DEV-0058, FR-26): região do corpo e descrição. É do aluno (BR-01): o personal
 * vinculado registra; ao incluir na ficha um exercício da região, o sistema alerta, sem bloquear (BR-11).
 *
 * @param clientId o {@code sub} do aluno (ADR-009)
 * @param recordedBy o {@code sub} do personal que registrou ou editou por último
 */
public record MobilityRestriction(
        UUID id,
        UUID clientId,
        BodyRegion region,
        String description,
        UUID recordedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MAX_DESCRIPTION_LENGTH = 500;

    public MobilityRestriction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(recordedBy, "recordedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (region == null) {
            throw new InvalidRestrictionException("region", "Informe a região do corpo.");
        }
        description = description == null ? "" : description.strip();
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidRestrictionException("description", "A descrição precisa ter de 1 a 500 caracteres.");
        }
    }

    /** Nova restrição registrada pelo personal. */
    public static MobilityRestriction record(
            UUID clientId, BodyRegion region, String description, UUID personalId, Instant now) {
        return new MobilityRestriction(UUID.randomUUID(), clientId, region, description, personalId, now, now);
    }

    /** A mesma restrição com a região e a descrição novas. */
    public MobilityRestriction change(BodyRegion newRegion, String newDescription, UUID personalId, Instant now) {
        return new MobilityRestriction(id, clientId, newRegion, newDescription, personalId, createdAt, now);
    }
}
