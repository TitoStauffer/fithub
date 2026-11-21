package com.fithub.patients.application.usecase;

import com.fithub.patients.domain.aggregate.Patient;
import com.fithub.patients.domain.service.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-PAT-001: Professional adds Patient
 */
@Service
public class CreatePatientUseCase {
    private static final Logger logger = LoggerFactory.getLogger(CreatePatientUseCase.class);
    private final PatientService patientService;

    public CreatePatientUseCase(PatientService patientService) {
        this.patientService = patientService;
    }

    @Transactional
    public CreatePatientResponse execute(CreatePatientRequest request) {
        logger.debug("Creating patient: name={}", request.getName());

        Patient patient = patientService.createPatient(
            UUID.fromString(request.getProfessionalId()),
            request.getName(),
            request.getWeight(),
            request.getAge(),
            request.getHeight()
        );

        logger.info("Patient created: id={}", patient.getId());

        return new CreatePatientResponse(
            patient.getId().toString(),
            patient.getName(),
            patient.getBiometrics().getWeight(),
            patient.getBiometrics().getAge(),
            patient.getBiometrics().getHeight(),
            patient.getBiometrics().getImc()
        );
    }

    public static class CreatePatientRequest {
        private String professionalId;
        private String name;
        private Double weight;
        private Integer age;
        private Double height;

        public CreatePatientRequest() {}

        public String getProfessionalId() { return professionalId; }
        public void setProfessionalId(String professionalId) { this.professionalId = professionalId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
    }

    public static class CreatePatientResponse {
        private String patientId;
        private String name;
        private Double weight;
        private Integer age;
        private Double height;
        private Double imc;

        public CreatePatientResponse(String patientId, String name, Double weight, Integer age, Double height, Double imc) {
            this.patientId = patientId;
            this.name = name;
            this.weight = weight;
            this.age = age;
            this.height = height;
            this.imc = imc;
        }

        public String getPatientId() { return patientId; }
        public String getName() { return name; }
        public Double getWeight() { return weight; }
        public Integer getAge() { return age; }
        public Double getHeight() { return height; }
        public Double getImc() { return imc; }
    }
}
