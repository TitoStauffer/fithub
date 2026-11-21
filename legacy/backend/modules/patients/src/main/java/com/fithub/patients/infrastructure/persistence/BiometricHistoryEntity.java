package com.fithub.patients.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_history")
public class BiometricHistoryEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    public BiometricHistoryEntity() {}

    public BiometricHistoryEntity(UUID id, UUID patientId, Double weight, LocalDateTime recordedAt) {
        this.id = id;
        this.patientId = patientId;
        this.weight = weight;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
