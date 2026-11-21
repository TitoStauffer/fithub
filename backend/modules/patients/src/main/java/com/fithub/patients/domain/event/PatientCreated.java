package com.fithub.patients.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public class PatientCreated {
    private final UUID patientId;
    private final UUID userId;
    private final String name;
    private final LocalDateTime createdAt;

    public PatientCreated(UUID patientId, UUID userId, String name, LocalDateTime createdAt) {
        this.patientId = patientId;
        this.userId = userId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getPatientId() { return patientId; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
