package com.fithub.training.domain.session;

/** A sessão não existe ou é de outro aluno: a resposta é a mesma, para não revelar que ela existe. */
public final class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException() {
        super("Sessão não encontrada.");
    }
}
