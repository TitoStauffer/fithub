package com.fithub.patients.domain.aggregate;

import com.fithub.patients.domain.valueobject.ProfessionalRole;
import com.fithub.patients.domain.valueobject.RelationshipStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agregado raiz PatientProfessionalRelationship: relacionamento entre paciente e profissional.
 * Invariantes:
 * - Não há duplicatas (mesmo PT + mesmo Paciente)
 * - Invitação token é único
 * - Status segue fluxo: PENDING → ACTIVE → INACTIVE
 */
public class PatientProfessionalRelationship {
    private final UUID id;
    private final UUID patientId;
    private final UUID professionalId;
    private final ProfessionalRole professionalRole;
    private RelationshipStatus status;
    private final String invitationToken;
    private LocalDateTime acceptedAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private PatientProfessionalRelationship(UUID id, UUID patientId, UUID professionalId,
                                           ProfessionalRole professionalRole, RelationshipStatus status,
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

    public static PatientProfessionalRelationship create(UUID patientId, UUID professionalId,
                                                         ProfessionalRole professionalRole, String invitationToken) {
        LocalDateTime now = LocalDateTime.now();
        return new PatientProfessionalRelationship(
            UUID.randomUUID(),
            patientId,
            professionalId,
            professionalRole,
            RelationshipStatus.PENDING,
            invitationToken,
            null,
            now,
            now
        );
    }

    public static PatientProfessionalRelationship fromDatabase(UUID id, UUID patientId, UUID professionalId,
                                                               ProfessionalRole professionalRole,
                                                               RelationshipStatus status, String invitationToken,
                                                               LocalDateTime acceptedAt, LocalDateTime createdAt,
                                                               LocalDateTime updatedAt) {
        return new PatientProfessionalRelationship(id, patientId, professionalId, professionalRole, status,
            invitationToken, acceptedAt, createdAt, updatedAt);
    }

    // ========== Métodos de Domínio ==========

    public void accept() {
        if (status != RelationshipStatus.PENDING) {
            throw new IllegalStateException("Only pending relationships can be accepted");
        }
        this.status = RelationshipStatus.ACTIVE;
        this.acceptedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.status = RelationshipStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == RelationshipStatus.ACTIVE;
    }

    public boolean isPending() {
        return status == RelationshipStatus.PENDING;
    }

    // ========== Getters ==========

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getProfessionalId() { return professionalId; }
    public ProfessionalRole getProfessionalRole() { return professionalRole; }
    public RelationshipStatus getStatus() { return status; }
    public String getInvitationToken() { return invitationToken; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
