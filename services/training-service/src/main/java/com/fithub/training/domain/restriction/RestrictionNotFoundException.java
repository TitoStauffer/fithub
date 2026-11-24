package com.fithub.training.domain.restriction;

/** A restrição não existe ou é de outro aluno: a resposta é a mesma, para não revelar que ela existe. */
public final class RestrictionNotFoundException extends RuntimeException {

    public RestrictionNotFoundException() {
        super("Restrição não encontrada.");
    }
}
