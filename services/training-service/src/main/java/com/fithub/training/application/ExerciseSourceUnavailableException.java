package com.fithub.training.application;

/** A base externa de exercícios falhou no meio da importação (DEV-0054); o que já foi importado fica. */
public class ExerciseSourceUnavailableException extends RuntimeException {

    public ExerciseSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExerciseSourceUnavailableException(String message) {
        super(message);
    }
}
