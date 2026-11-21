package com.fithub.patients.domain.aggregate;

import com.fithub.patients.domain.valueobject.RestrictionType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity: restrição de saúde do paciente.
 * Não é agregado raiz, pertence ao Patient.
 */
public class HealthRestriction {
    private final UUID id;
    private final RestrictionType type;
    private final String description;
    private final LocalDateTime createdAt;

    private HealthRestriction(UUID id, RestrictionType type, String description, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static HealthRestriction create(RestrictionType type, String description) {
        return new HealthRestriction(UUID.randomUUID(), type, description, LocalDateTime.now());
    }

    public static HealthRestriction fromDatabase(UUID id, RestrictionType type, String description, LocalDateTime createdAt) {
        return new HealthRestriction(id, type, description, createdAt);
    }

    public UUID getId() { return id; }
    public RestrictionType getType() { return type; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthRestriction that = (HealthRestriction) o;
        return Objects.equals(id, that.id) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
