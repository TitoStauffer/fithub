package com.fithub.client.domain.invitation;

/** Dado do convite fora da regra. A mensagem é para a pessoa, em português, e nunca repete o valor. */
public final class InvalidInvitationException extends RuntimeException {

    private final String field;

    public InvalidInvitationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
