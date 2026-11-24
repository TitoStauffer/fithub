package com.fithub.nutrition.domain.food;

/** A medida não existe, é de outro alimento ou de outra nutricionista: a resposta é a mesma. */
public final class FoodMeasureNotFoundException extends RuntimeException {

    public FoodMeasureNotFoundException() {
        super("Medida não encontrada.");
    }
}
