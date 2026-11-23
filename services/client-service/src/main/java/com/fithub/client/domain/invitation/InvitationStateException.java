package com.fithub.client.domain.invitation;

/** Operação que o estado atual do convite não permite (por exemplo, cancelar um convite aceito). */
public final class InvitationStateException extends RuntimeException {

    public InvitationStateException(String message) {
        super(message);
    }
}
