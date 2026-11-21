package com.fithub.patients.infrastructure.persistence;

import com.fithub.patients.domain.aggregate.Patient;
import com.fithub.patients.domain.valueobject.Biometrics;
import com.fithub.patients.domain.valueobject.PatientStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class PatientEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private Double height;

    @Column(nullable = false)
    private Double imc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PatientEntity() {}

    public static PatientEntity fromAggregate(Patient patient) {
        PatientEntity entity = new PatientEntity();
        entity.id = patient.getId();
        entity.userId = patient.getUserId();
        entity.name = patient.getName();
        entity.weight = patient.getBiometrics().getWeight();
        entity.age = patient.getBiometrics().getAge();
        entity.height = patient.getBiometrics().getHeight();
        entity.imc = patient.getBiometrics().getImc();
        entity.status = patient.getStatus();
        entity.createdAt = patient.getCreatedAt();
        entity.updatedAt = patient.getUpdatedAt();
        return entity;
    }

    public Patient toAggregate() {
        return Patient.fromDatabase(
            id, userId, name,
            Biometrics.create(weight, age, height),
            null, // Restrições carregadas separadamente
            status, createdAt, updatedAt
        );
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
    public Double getImc() { return imc; }
    public void setImc(Double imc) { this.imc = imc; }
    public PatientStatus getStatus() { return status; }
    public void setStatus(PatientStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
