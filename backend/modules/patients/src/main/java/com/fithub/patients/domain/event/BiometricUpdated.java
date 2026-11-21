package com.fithub.patients.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public class BiometricUpdated {
    private final UUID patientId;
    private final Double weight;
    private final LocalDateTime recordedAt;

    public BiometricUpdated(UUID patientId, Double weight, LocalDateTime recordedAt) {
        this.patientId = patientId;
        this.weight = weight;
        this.recordedAt = recordedAt;
    }

    public UUID getPatientId() { return patientId; }
    public Double getWeight() { return weight; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
