package com.fithub.patients.domain.repository;

import com.fithub.patients.domain.aggregate.PatientProfessionalRelationship;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientProfessionalRelationshipRepository {
    void save(PatientProfessionalRelationship relationship);
    Optional<PatientProfessionalRelationship> findById(UUID id);
    List<PatientProfessionalRelationship> findByPatientId(UUID patientId);
    List<PatientProfessionalRelationship> findByProfessionalId(UUID professionalId);
    Optional<PatientProfessionalRelationship> findByPatientIdAndProfessionalId(UUID patientId, UUID professionalId);
    boolean existsByPatientIdAndProfessionalId(UUID patientId, UUID professionalId);
    Optional<PatientProfessionalRelationship> findByInvitationToken(String token);
}
