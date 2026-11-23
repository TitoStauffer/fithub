package com.fithub.account.domain.professional;

/** Dado do cadastro do profissional fora da regra. A mensagem é para a pessoa, em português. */
public final class InvalidRegistrationException extends RuntimeException {

    private final String field;

    public InvalidRegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Campo da requisição que causou o erro. */
    public String field() {
        return field;
    }
}
