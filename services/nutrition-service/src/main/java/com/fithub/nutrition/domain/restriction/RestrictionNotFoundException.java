package com.fithub.nutrition.domain.restriction;

/** A restrição não existe ou é de outro paciente: a resposta é a mesma, para não revelar que ela existe. */
public final class RestrictionNotFoundException extends RuntimeException {

    public RestrictionNotFoundException() {
        super("Restrição não encontrada.");
    }
}
