package com.fithub.nutrition.domain.repository;

import com.fithub.nutrition.domain.aggregate.MealExecution;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealExecutionRepository {
    void save(MealExecution execution);
    Optional<MealExecution> findById(UUID id);
    List<MealExecution> findByPatientIdAndDate(UUID patientId, LocalDate date);
    List<MealExecution> findByPatientIdLast30Days(UUID patientId);
}
