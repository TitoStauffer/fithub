package com.fithub.client.domain.profile;

/** O perfil de saúde exige o perfil do cliente (DEV-0032) já criado. */
public final class ProfileRequiredException extends RuntimeException {

    public ProfileRequiredException() {
        this("Crie seu perfil antes de informar os dados de saúde.");
    }

    /** Mesmo motivo, com a mensagem da ação que pediu o perfil. */
    public ProfileRequiredException(String message) {
        super(message);
    }
}
