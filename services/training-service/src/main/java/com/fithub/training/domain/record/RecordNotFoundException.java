package com.fithub.training.domain.record;

/** O recorde manual não existe ou é de outro aluno: a resposta é a mesma. */
public final class RecordNotFoundException extends RuntimeException {

    public RecordNotFoundException() {
        super("Recorde não encontrado.");
    }
}
