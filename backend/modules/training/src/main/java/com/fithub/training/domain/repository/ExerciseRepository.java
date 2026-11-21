package com.fithub.training.domain.repository;

import com.fithub.training.domain.aggregate.Exercise;
import com.fithub.training.domain.valueobject.MuscleGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseRepository {
    void save(Exercise exercise);
    Optional<Exercise> findById(UUID id);
    Optional<Exercise> findByExternalId(String externalId);
    List<Exercise> findAllByMuscleGroup(MuscleGroup muscleGroup);
    List<Exercise> findAll();
}
