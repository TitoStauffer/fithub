package com.fithub.nutrition.domain.food;

/** Campo do alimento fora das regras; o campo vai na resposta para a tela apontar. */
public final class InvalidFoodException extends RuntimeException {

    private final String field;

    public InvalidFoodException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
