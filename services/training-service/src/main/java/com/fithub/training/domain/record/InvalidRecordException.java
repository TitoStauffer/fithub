package com.fithub.training.domain.record;

/** Recorde manual com um campo fora das regras; {@code field} diz qual, para a API apontar o campo. */
public final class InvalidRecordException extends RuntimeException {

    private final String field;

    public InvalidRecordException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
