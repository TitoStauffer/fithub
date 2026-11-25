package com.fithub.nutrition.domain.log;

/** O registro não existe ou é de outro paciente: a resposta é a mesma, para não revelar que ele existe. */
public final class MealLogNotFoundException extends RuntimeException {

    public MealLogNotFoundException() {
        super("Registro de refeição não encontrado.");
    }
}
