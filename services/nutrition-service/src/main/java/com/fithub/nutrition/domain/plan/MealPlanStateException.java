package com.fithub.nutrition.domain.plan;

/** A operação não vale no estado atual do plano (ex.: publicar de novo). */
public final class MealPlanStateException extends RuntimeException {

    public MealPlanStateException(String message) {
        super(message);
    }
}
