package com.fithub.training.domain.restriction;

/** Restrição com um campo fora das regras; {@code field} diz qual, para a API apontar o campo. */
public final class InvalidRestrictionException extends RuntimeException {

    private final String field;

    public InvalidRestrictionException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
