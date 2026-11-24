package com.fithub.nutrition.domain.food;

/** O alimento não existe ou é próprio de outra nutricionista: a resposta é a mesma, para não revelar que existe. */
public final class FoodNotFoundException extends RuntimeException {

    public FoodNotFoundException() {
        super("Alimento não encontrado.");
    }
}
