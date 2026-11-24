package com.fithub.training.domain.plan;

/** Ficha com um campo fora das regras; {@code field} diz qual, para a API apontar o campo. */
public final class InvalidPlanException extends RuntimeException {

    private final String field;

    public InvalidPlanException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
