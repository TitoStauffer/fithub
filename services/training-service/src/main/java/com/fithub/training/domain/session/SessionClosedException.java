package com.fithub.training.domain.session;

/** O prazo de correção do treino acabou: os registros ficam imutáveis depois do dia (BR-08). */
public final class SessionClosedException extends RuntimeException {

    public SessionClosedException() {
        super("O prazo para corrigir este treino acabou: os registros só podem ser corrigidos no mesmo dia.");
    }
}
