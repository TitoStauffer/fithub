package com.fithub.patients.domain.repository;

import com.fithub.patients.domain.aggregate.Patient;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface PatientRepository {
    void save(Patient patient);
    Optional<Patient> findById(UUID id);
    Optional<Patient> findByUserId(UUID userId);
    List<Patient> findAll();
    /**
     * Busca todos os pacientes de um profissional (PT ou Nutritionist)
     */
    List<Patient> findByProfessionalUserId(UUID professionalUserId);
}
