package com.fithub.patients.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity para relacionamento entre Patient e Professional (PT ou Nutritionist)
 */
@Entity
@Table(name = "patient_professional_relationships")
public class PatientProfessionalRelationshipEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "professional_role", nullable = false, length = 20)
    private String professionalRole; // PT, NUTRITIONIST

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, ACTIVE, INACTIVE

    @Column(name = "invitation_token", unique = true)
    private String invitationToken;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PatientProfessionalRelationshipEntity() {}

    public PatientProfessionalRelationshipEntity(UUID id, UUID patientId, UUID professionalId,
                                                  String professionalRole, String status,
                                                  String invitationToken, LocalDateTime acceptedAt,
                                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.professionalId = professionalId;
        this.professionalRole = professionalRole;
        this.status = status;
        this.invitationToken = invitationToken;
        this.acceptedAt = acceptedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public UUID getProfessionalId() { return professionalId; }
    public void setProfessionalId(UUID professionalId) { this.professionalId = professionalId; }

    public String getProfessionalRole() { return professionalRole; }
    public void setProfessionalRole(String professionalRole) { this.professionalRole = professionalRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInvitationToken() { return invitationToken; }
    public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
