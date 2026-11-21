package com.fithub.patients.domain.aggregate;

import com.fithub.patients.domain.valueobject.Biometrics;
import com.fithub.patients.domain.valueobject.PatientStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado raiz Patient: dados biométricos e relacionamento com profissionais.
 * Invariantes:
 * - Biometria válida (peso, idade, altura > 0)
 * - IMC calculado automaticamente
 * - Restrições são imutáveis (histórico preservado)
 */
public class Patient {
    private final UUID id;
    private final UUID userId;
    private final String name;
    private Biometrics biometrics;
    private final List<HealthRestriction> restrictions;
    private PatientStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Patient(UUID id, UUID userId, String name, Biometrics biometrics,
                    List<HealthRestriction> restrictions, PatientStatus status,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.biometrics = biometrics;
        this.restrictions = restrictions != null ? new ArrayList<>(restrictions) : new ArrayList<>();
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Patient create(UUID userId, String name, Double weight, Integer age, Double height) {
        validateName(name);
        Biometrics biometrics = Biometrics.create(weight, age, height);
        LocalDateTime now = LocalDateTime.now();

        return new Patient(
            UUID.randomUUID(),
            userId,
            name,
            biometrics,
            new ArrayList<>(),
            PatientStatus.ACTIVE,
            now,
            now
        );
    }

    public static Patient fromDatabase(UUID id, UUID userId, String name, Biometrics biometrics,
                                       List<HealthRestriction> restrictions, PatientStatus status,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Patient(id, userId, name, biometrics, restrictions, status, createdAt, updatedAt);
    }

    // ========== Métodos de Domínio ==========

    public void updateBiometrics(Double weight, Integer age, Double height) {
        Biometrics newBiometrics = Biometrics.create(weight, age, height);
        this.biometrics = newBiometrics;
        this.updatedAt = LocalDateTime.now();
    }

    public void addRestriction(HealthRestriction restriction) {
        if (!restrictions.contains(restriction)) {
            restrictions.add(restriction);
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void deactivate() {
        this.status = PatientStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = PatientStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == PatientStatus.ACTIVE;
    }

    // ========== Getters ==========

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public Biometrics getBiometrics() { return biometrics; }
    public List<HealthRestriction> getRestrictions() { return new ArrayList<>(restrictions); }
    public PatientStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Name cannot exceed 255 characters");
        }
    }
}
