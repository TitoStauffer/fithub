package com.fithub.training.domain.plan;

/** A ficha não existe ou não é do personal: a resposta é a mesma, para não revelar que ela existe. */
public final class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException() {
        super("Ficha não encontrada.");
    }
}
