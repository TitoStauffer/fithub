package com.fithub.training.domain.exercise;

/** Exercício com um campo fora das regras; {@code field} diz qual, para a API apontar o campo. */
public final class InvalidExerciseException extends RuntimeException {

    private final String field;

    public InvalidExerciseException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
