package com.fithub.nutrition.domain.log;

/** Campo do registro de refeição fora das regras; o campo vai na resposta para a tela apontar. */
public final class InvalidMealLogException extends RuntimeException {

    private final String field;

    public InvalidMealLogException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
