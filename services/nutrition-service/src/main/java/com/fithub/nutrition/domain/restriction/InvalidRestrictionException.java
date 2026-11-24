package com.fithub.nutrition.domain.restriction;

/** Campo da restrição alimentar fora das regras; o campo vai na resposta para a tela apontar. */
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
