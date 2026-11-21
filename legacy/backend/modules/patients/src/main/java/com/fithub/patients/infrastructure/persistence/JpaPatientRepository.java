package com.fithub.patients.infrastructure.persistence;

import com.fithub.patients.domain.aggregate.Patient;
import com.fithub.patients.domain.repository.PatientRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA do PatientRepository
 * Usa factory methods da entity para mapping
 */
@Repository
public class JpaPatientRepository implements PatientRepository {
    private final PatientJpaRepository patientJpaRepository;

    public JpaPatientRepository(PatientJpaRepository patientJpaRepository) {
        this.patientJpaRepository = patientJpaRepository;
    }

    @Override
    public void save(Patient patient) {
        PatientEntity entity = PatientEntity.fromAggregate(patient);
        patientJpaRepository.save(entity);
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return patientJpaRepository.findById(id)
            .map(PatientEntity::toAggregate);
    }

    @Override
    public Optional<Patient> findByUserId(UUID userId) {
        return patientJpaRepository.findByUserId(userId)
            .map(PatientEntity::toAggregate);
    }

    @Override
    public List<Patient> findAll() {
        return patientJpaRepository.findAll()
            .stream()
            .map(PatientEntity::toAggregate)
            .toList();
    }

    @Override
    public List<Patient> findByProfessionalUserId(UUID professionalUserId) {
        return patientJpaRepository.findByProfessionalUserId(professionalUserId)
            .stream()
            .map(PatientEntity::toAggregate)
            .toList();
    }
}
