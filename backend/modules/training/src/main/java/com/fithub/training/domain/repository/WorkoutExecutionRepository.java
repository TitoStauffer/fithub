package com.fithub.training.domain.repository;

import com.fithub.training.domain.aggregate.WorkoutExecution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutExecutionRepository {
    void save(WorkoutExecution execution);
    Optional<WorkoutExecution> findById(UUID id);
    List<WorkoutExecution> findByPatientIdLast30Days(UUID patientId);
    List<WorkoutExecution> findByPatientIdAndExerciseId(UUID patientId, UUID exerciseId);
}
