package com.fithub.client.application;

/**
 * A conta tem o papel de profissional, mas o {@code ProfissionalCadastrado} ainda não chegou ao client-service
 * (consistência eventual, ADR-003). Some em segundos.
 */
public final class ProfessionalNotReadyException extends RuntimeException {

    public ProfessionalNotReadyException() {
        super("Seu cadastro de profissional ainda está sendo processado. Tente de novo em instantes.");
    }
}
