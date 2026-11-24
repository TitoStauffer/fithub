package com.fithub.training.application;

import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseNotFoundException;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.InvalidExerciseException;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Exercícios próprios do personal (DEV-0056, FR-24): criar, editar e arquivar. O dono vem sempre do token; só ele
 * altera, e o do catálogo não é de ninguém (BR-13).
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: excluir arquiva em vez de apagar, porque fichas antigas
 * continuam mostrando o exercício; exercício de outro personal ou do catálogo responde como inexistente.
 */
@Service
public class ManageOwnExercises {

    private final ExerciseRepository exercises;
    private final Clock clock;

    public ManageOwnExercises(ExerciseRepository exercises, Clock clock) {
        this.exercises = exercises;
        this.clock = clock;
    }

    /** O que o personal informa para criar ou editar. */
    public record Draft(
            String name, Set<MuscleGroup> muscleGroups, Equipment equipment, String instructions, String mediaUrl) {}

    /**
     * Cria o exercício próprio de quem chama.
     *
     * @param personalAccount o {@code sub} do token (ADR-009)
     */
    public Exercise create(String personalAccount, Draft draft) {
        UUID owner = ViewExercise.accountId(personalAccount);
        if (owner == null) {
            throw new InvalidExerciseException("ownerId", "A conta não tem um identificador válido.");
        }
        Exercise exercise = Exercise.newOwn(
                owner,
                draft.name(),
                draft.muscleGroups(),
                draft.equipment(),
                draft.instructions(),
                draft.mediaUrl(),
                clock.instant());
        exercises.save(exercise);
        return exercise;
    }

    /**
     * Troca os campos do exercício próprio de quem chama.
     *
     * @throws ExerciseNotFoundException se ele não existe, é de outro personal ou do catálogo
     */
    public Exercise update(String personalAccount, UUID exerciseId, Draft draft) {
        Exercise edited = owned(personalAccount, exerciseId)
                .edit(
                        draft.name(),
                        draft.muscleGroups(),
                        draft.equipment(),
                        draft.instructions(),
                        draft.mediaUrl(),
                        clock.instant());
        exercises.save(edited);
        return edited;
    }

    /**
     * Arquiva o exercício próprio de quem chama; arquivar de novo não muda nada.
     *
     * @throws ExerciseNotFoundException se ele não existe, é de outro personal ou do catálogo
     */
    public void archive(String personalAccount, UUID exerciseId) {
        Exercise exercise = owned(personalAccount, exerciseId);
        if (!exercise.archived()) {
            exercises.save(exercise.archive(clock.instant()));
        }
    }

    private Exercise owned(String personalAccount, UUID exerciseId) {
        UUID personalId = ViewExercise.accountId(personalAccount);
        return exercises
                .findById(exerciseId)
                .filter(exercise -> exercise.ownedBy(personalId))
                .orElseThrow(ExerciseNotFoundException::new);
    }
}
