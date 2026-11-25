package com.fithub.client.domain.profile;

import java.time.Instant;
import java.util.UUID;

/** Eventos do cliente para os outros serviços (porta do domínio). */
public interface ClientEvents {

    /**
     * Os dados básicos do cliente mudaram (DEV-0034, {@code ClienteAtualizado}). Chamado dentro da
     * transação da gravação: se ela for desfeita, o evento também é.
     */
    void updated(ClientProfile profile);

    /**
     * O cliente excluiu a conta (DEV-0105, {@code ContaExcluida}). Chamado dentro da transação da exclusão: se ela
     * for desfeita, o evento também é.
     */
    void deleted(UUID clientId, Instant at);
}
