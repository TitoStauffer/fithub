package com.fithub.training.domain.exercise;

import java.util.Optional;
import java.util.UUID;

/** Exercícios do catálogo e próprios (DEV-0053). */
public interface ExerciseRepository {

    void save(Exercise exercise);

    Optional<Exercise> findById(UUID id);
}
