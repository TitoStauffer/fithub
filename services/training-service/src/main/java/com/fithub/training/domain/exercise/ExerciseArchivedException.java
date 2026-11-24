package com.fithub.training.domain.exercise;

/** O exercício próprio foi arquivado pelo dono e não muda mais (DEV-0056). */
public final class ExerciseArchivedException extends RuntimeException {

    public ExerciseArchivedException() {
        super("Exercício arquivado não pode ser editado.");
    }
}
