package com.fithub.training.domain.access;

/**
 * O profissional não tem vínculo ativo com o cliente (BR-02). Vira 404, como se o cliente não existisse: a
 * resposta não revela quem usa o FitHub.
 */
public final class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException() {
        super("Cliente não encontrado.");
    }
}
