package com.fithub.nutrition.domain.plan;

/** O plano não existe ou não é da nutricionista: a resposta é a mesma, para não revelar que ele existe. */
public final class MealPlanNotFoundException extends RuntimeException {

    public MealPlanNotFoundException() {
        super("Plano alimentar não encontrado.");
    }
}
