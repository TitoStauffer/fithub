package com.fithub.nutrition.domain.log;

/** O registro não pode ser feito ou corrigido assim (outro dia, outra refeição, refeição já registrada). */
public final class MealLogConflictException extends RuntimeException {

    public MealLogConflictException(String message) {
        super(message);
    }
}
