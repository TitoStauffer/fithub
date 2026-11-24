package com.fithub.training.domain.session;

/** Série com um campo fora das regras; {@code field} diz qual, para a API apontar o campo. */
public final class InvalidSetException extends RuntimeException {

    private final String field;

    public InvalidSetException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
