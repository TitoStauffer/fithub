package com.fithub.nutrition.application.usecase;

import com.fithub.nutrition.domain.aggregate.DietPlan;
import com.fithub.nutrition.domain.service.NutritionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateDietPlanUseCase {
    private final NutritionService nutritionService;

    public CreateDietPlanUseCase(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @Transactional
    public CreateDietPlanResponse execute(CreateDietPlanRequest request) {
        DietPlan plan = nutritionService.createDietPlan(
            UUID.fromString(request.getPatientId()),
            UUID.fromString(request.getNutritionistId())
        );
        return new CreateDietPlanResponse(plan.getId().toString(), plan.getVersion());
    }

    public static class CreateDietPlanRequest {
        private String patientId;
        private String nutritionistId;

        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public String getNutritionistId() { return nutritionistId; }
        public void setNutritionistId(String nutritionistId) { this.nutritionistId = nutritionistId; }
    }

    public static class CreateDietPlanResponse {
        private String planId;
        private Integer version;

        public CreateDietPlanResponse(String planId, Integer version) {
            this.planId = planId;
            this.version = version;
        }

        public String getPlanId() { return planId; }
        public Integer getVersion() { return version; }
    }
}
