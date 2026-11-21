package com.fithub.training.application.usecase;

import com.fithub.training.domain.aggregate.WorkoutPlan;
import com.fithub.training.domain.service.TrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateWorkoutPlanUseCase {
    private final TrainingService trainingService;

    public CreateWorkoutPlanUseCase(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Transactional
    public CreateWorkoutPlanResponse execute(CreateWorkoutPlanRequest request) {
        WorkoutPlan plan = trainingService.createWorkoutPlan(
            UUID.fromString(request.getPatientId()),
            UUID.fromString(request.getPtId())
        );

        return new CreateWorkoutPlanResponse(plan.getId().toString(), plan.getVersion());
    }

    public static class CreateWorkoutPlanRequest {
        private String patientId;
        private String ptId;

        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public String getPtId() { return ptId; }
        public void setPtId(String ptId) { this.ptId = ptId; }
    }

    public static class CreateWorkoutPlanResponse {
        private String planId;
        private Integer version;

        public CreateWorkoutPlanResponse(String planId, Integer version) {
            this.planId = planId;
            this.version = version;
        }

        public String getPlanId() { return planId; }
        public Integer getVersion() { return version; }
    }
}
