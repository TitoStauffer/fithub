package com.fithub.account.domain.login;

import java.util.UUID;

/** Contas de login no provedor de identidade (porta do domínio, DEV-0161, ADR-012). */
public interface LoginAccounts {

    /**
     * Apaga a conta de login. Conta que já não existe conta como apagada: repetir não tem efeito. Lança exceção se o
     * provedor não confirmar, para o consumo ser tentado de novo.
     */
    void delete(UUID accountId);
}
