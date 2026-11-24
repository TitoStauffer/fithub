package com.fithub.training.domain.session;

/** Já há uma sessão aberta nessa ficha, ou o id já é de outra sessão. */
public final class SessionConflictException extends RuntimeException {

    public SessionConflictException(String message) {
        super(message);
    }
}
