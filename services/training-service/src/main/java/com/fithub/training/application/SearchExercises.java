package com.fithub.training.application;

import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.ExerciseSearch;
import org.springframework.stereotype.Service;

/**
 * Busca de exercícios pelo personal (DEV-0055, FR-23): o catálogo comum mais os próprios de quem busca; o próprio
 * de outro personal nunca aparece (BR-13).
 */
@Service
public class SearchExercises {

    private final ExerciseRepository exercises;

    public SearchExercises(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    /**
     * Uma página da busca.
     *
     * @param personalAccount o {@code sub} do token (ADR-009); fora do formato UUID, vê só o catálogo
     */
    public ExerciseSearch.Page search(String personalAccount, ExerciseSearch search) {
        return exercises.search(search, ViewExercise.accountId(personalAccount));
    }
}
