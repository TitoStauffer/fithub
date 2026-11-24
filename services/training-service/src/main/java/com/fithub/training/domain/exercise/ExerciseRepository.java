package com.fithub.training.domain.exercise;

import java.util.Optional;
import java.util.UUID;

/** Exercícios do catálogo e próprios (DEV-0053). */
public interface ExerciseRepository {

    void save(Exercise exercise);

    Optional<Exercise> findById(UUID id);

    /** Exercício importado de uma base externa, pelo id que ele tem lá (DEV-0054). */
    Optional<Exercise> findByExternalId(String source, String externalId);

    /**
     * Catálogo mais os exercícios próprios do personal (BR-13) que atendem à busca, ordenados por nome (DEV-0055).
     *
     * @param personalId dono dos exercícios próprios que entram; {@code null} traz só o catálogo
     */
    ExerciseSearch.Page search(ExerciseSearch search, UUID personalId);
}
