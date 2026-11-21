package com.fithub.patients.domain.aggregate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: histórico de biometria do paciente.
 * Rastreia peso ao longo do tempo para gráficos de evolução.
 */
public class BiometricHistory {
    private final UUID id;
    private final Double weight;
    private final LocalDateTime recordedAt;

    private BiometricHistory(UUID id, Double weight, LocalDateTime recordedAt) {
        this.id = id;
        this.weight = weight;
        this.recordedAt = recordedAt;
    }

    public static BiometricHistory create(Double weight) {
        return new BiometricHistory(UUID.randomUUID(), weight, LocalDateTime.now());
    }

    public static BiometricHistory fromDatabase(UUID id, Double weight, LocalDateTime recordedAt) {
        return new BiometricHistory(id, weight, recordedAt);
    }

    public UUID getId() { return id; }
    public Double getWeight() { return weight; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
