package com.fithub.nutrition.domain.repository;

import com.fithub.nutrition.domain.aggregate.DietPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DietPlanRepository {
    void save(DietPlan plan);
    Optional<DietPlan> findById(UUID id);
    Optional<DietPlan> findByPatientIdAndLatestVersion(UUID patientId);
    List<DietPlan> findByPatientIdAllVersions(UUID patientId);
}
