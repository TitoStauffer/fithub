package com.fithub.patients.domain.service;

import com.fithub.patients.domain.aggregate.Patient;
import com.fithub.patients.domain.aggregate.BiometricHistory;
import com.fithub.patients.domain.event.PatientCreated;
import com.fithub.patients.domain.event.BiometricUpdated;
import com.fithub.patients.domain.repository.PatientRepository;
import com.fithub.patients.domain.repository.BiometricHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

/**
 * Domain Service: lógica de negócio para gerenciar pacientes.
 */
@Service
public class PatientService {
    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);
    private final PatientRepository patientRepository;
    private final BiometricHistoryRepository biometricHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PatientService(PatientRepository patientRepository,
                         BiometricHistoryRepository biometricHistoryRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.patientRepository = patientRepository;
        this.biometricHistoryRepository = biometricHistoryRepository;
        this.eventPublisher = eventPublisher;
    }

    public Patient createPatient(UUID professionalId, String name, Double weight, Integer age, Double height) {
        logger.debug("Creating patient: name={}", name);

        Patient patient = Patient.create(professionalId, name, weight, age, height);
        patientRepository.save(patient);

        // Registra biometria inicial no histórico
        BiometricHistory initialRecord = BiometricHistory.create(weight);
        biometricHistoryRepository.save(initialRecord);

        // Emite evento
        eventPublisher.publishEvent(new PatientCreated(patient.getId(), patient.getUserId(), name, patient.getCreatedAt()));

        logger.info("Patient created: id={}, name={}", patient.getId(), name);
        return patient;
    }

    public void updateBiometrics(UUID patientId, Double weight, Integer age, Double height) {
        logger.debug("Updating biometrics for patient: id={}, weight={}", patientId, weight);

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        patient.updateBiometrics(weight, age, height);
        patientRepository.save(patient);

        // Registra no histórico
        BiometricHistory record = BiometricHistory.create(weight);
        biometricHistoryRepository.save(record);

        // Emite evento
        eventPublisher.publishEvent(new BiometricUpdated(patientId, weight, record.getRecordedAt()));

        logger.info("Biometrics updated for patient: id={}", patientId);
    }
}
