package com.fithub.patients.application.usecase;

import com.fithub.patients.domain.service.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-PAT-002: Update Patient Biometrics
 */
@Service
public class UpdateBiometricsUseCase {
    private static final Logger logger = LoggerFactory.getLogger(UpdateBiometricsUseCase.class);
    private final PatientService patientService;

    public UpdateBiometricsUseCase(PatientService patientService) {
        this.patientService = patientService;
    }

    @Transactional
    public UpdateBiometricsResponse execute(UpdateBiometricsRequest request) {
        logger.debug("Updating biometrics for patient: id={}", request.getPatientId());

        patientService.updateBiometrics(
            UUID.fromString(request.getPatientId()),
            request.getWeight(),
            request.getAge(),
            request.getHeight()
        );

        logger.info("Biometrics updated: patientId={}", request.getPatientId());

        return new UpdateBiometricsResponse("Biometrics updated successfully");
    }

    public static class UpdateBiometricsRequest {
        private String patientId;
        private Double weight;
        private Integer age;
        private Double height;

        public UpdateBiometricsRequest() {}

        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
    }

    public static class UpdateBiometricsResponse {
        private String message;

        public UpdateBiometricsResponse(String message) { this.message = message; }

        public String getMessage() { return message; }
    }
}
