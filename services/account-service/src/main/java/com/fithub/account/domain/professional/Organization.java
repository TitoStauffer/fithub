package com.fithub.account.domain.professional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Organização a que o profissional pertence. No MVP, todo profissional autônomo é dono de uma
 * organização de uma pessoa (decisão de produto de 21/11/2026); academias com várias pessoas ficam
 * para depois, sem mudar o modelo.
 *
 * @param ownerAccountId {@code sub} da conta dona
 */
public record Organization(UUID id, String ownerAccountId, Instant createdAt) {

    public Organization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (ownerAccountId == null || ownerAccountId.isBlank()) {
            throw new IllegalArgumentException("Organização sem dono.");
        }
    }
}
