package com.fithub.client.domain.assessment;

/** Dado da avaliação fora da regra. A mensagem é para a pessoa, em português, e nunca repete o valor. */
public final class InvalidAssessmentException extends RuntimeException {

    private final String field;

    public InvalidAssessmentException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
