package com.fithub.client.domain.profile;

/** O perfil de saúde exige o perfil do cliente (DEV-0032) já criado. */
public final class ProfileRequiredException extends RuntimeException {

    public ProfileRequiredException() {
        super("Crie seu perfil antes de informar os dados de saúde.");
    }
}
