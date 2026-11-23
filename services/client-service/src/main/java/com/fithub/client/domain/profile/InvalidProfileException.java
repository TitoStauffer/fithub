package com.fithub.client.domain.profile;

/** Dado do perfil fora da regra. A mensagem é para a pessoa, em português. */
public final class InvalidProfileException extends RuntimeException {

    private final String field;

    public InvalidProfileException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Campo da requisição que causou o erro. */
    public String field() {
        return field;
    }
}
