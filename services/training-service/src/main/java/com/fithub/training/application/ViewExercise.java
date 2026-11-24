package com.fithub.training.application;

import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.ExerciseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Leitura de um exercício pelo personal (DEV-0053): o do catálogo ou o próprio; o de outro não aparece (BR-13). */
@Service
public class ViewExercise {

    private final ExerciseRepository exercises;

    public ViewExercise(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    /**
     * Exercício visível para o personal.
     *
     * @param personalAccount o {@code sub} do token (ADR-009)
     */
    public Exercise view(String personalAccount, UUID exerciseId) {
        UUID personalId = accountId(personalAccount);
        return exercises
                .findById(exerciseId)
                .filter(exercise -> exercise.visibleTo(personalId))
                .orElseThrow(ExerciseNotFoundException::new);
    }

    /** Conta sem {@code sub} em UUID não é dona de nada: vê só o catálogo. */
    static UUID accountId(String account) {
        if (account == null) {
            return null;
        }
        try {
            return UUID.fromString(account);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
