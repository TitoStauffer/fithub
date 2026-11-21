package com.fithub.training.domain.repository;

import com.fithub.training.domain.aggregate.WorkoutPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutPlanRepository {
    void save(WorkoutPlan plan);
    Optional<WorkoutPlan> findById(UUID id);
    Optional<WorkoutPlan> findByPatientIdAndLatestVersion(UUID patientId);
    List<WorkoutPlan> findByPatientIdAllVersions(UUID patientId);
    List<WorkoutPlan> findByPtId(UUID ptId);
}
