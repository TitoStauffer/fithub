package com.fithub.client.domain.profile;

/** Eventos do perfil do cliente para os outros serviços (porta do domínio). */
public interface ClientEvents {

    /**
     * Os dados básicos do cliente mudaram (DEV-0034, {@code ClienteAtualizado}). Chamado dentro da
     * transação da gravação: se ela for desfeita, o evento também é.
     */
    void updated(ClientProfile profile);
}
