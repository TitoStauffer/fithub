package com.fithub.nutrition.domain.service;

import com.fithub.nutrition.domain.aggregate.DietPlan;
import com.fithub.nutrition.domain.aggregate.MealExecution;
import com.fithub.nutrition.domain.repository.DietPlanRepository;
import com.fithub.nutrition.domain.repository.MealExecutionRepository;
import com.fithub.nutrition.domain.valueobject.Macros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class NutritionService {
    private static final Logger logger = LoggerFactory.getLogger(NutritionService.class);
    private final DietPlanRepository dietPlanRepository;
    private final MealExecutionRepository mealExecutionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NutritionService(DietPlanRepository dietPlanRepository,
                           MealExecutionRepository mealExecutionRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.dietPlanRepository = dietPlanRepository;
        this.mealExecutionRepository = mealExecutionRepository;
        this.eventPublisher = eventPublisher;
    }

    public DietPlan createDietPlan(UUID patientId, UUID nutritionistId) {
        logger.debug("Creating diet plan for patient: {}", patientId);
        DietPlan plan = DietPlan.create(patientId, nutritionistId);
        dietPlanRepository.save(plan);
        return plan;
    }

    public MealExecution recordMealExecution(UUID patientId, UUID mealId, LocalDate date,
                                            Boolean completed, Double proteinOverride, String notes, String feeling) {
        logger.debug("Recording meal execution: patient={}, meal={}", patientId, mealId);

        DietPlan plan = dietPlanRepository.findByPatientIdAndLatestVersion(patientId)
            .orElseThrow(() -> new IllegalArgumentException("No active diet plan"));

        Macros macros = Macros.create(proteinOverride != null ? proteinOverride : 0.0, 0.0, 0.0);
        MealExecution execution = MealExecution.create(patientId, plan.getId(), mealId, date,
            completed, macros, notes, feeling);

        mealExecutionRepository.save(execution);
        logger.info("Meal execution recorded: id={}", execution.getId());
        return execution;
    }
}
