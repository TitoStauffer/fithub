package com.fithub.nutrition.domain.plan;

/** Campo do plano alimentar fora das regras; o campo vai na resposta para a tela apontar. */
public final class InvalidMealPlanException extends RuntimeException {

    private final String field;

    public InvalidMealPlanException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }

    /** O mesmo erro com o caminho do item, da refeição ou da lista na frente do campo. */
    public InvalidMealPlanException under(String prefix) {
        return new InvalidMealPlanException(prefix + "." + field, getMessage());
    }
}
