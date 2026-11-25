package com.fithub.client.domain.account;

import java.time.Instant;
import java.util.UUID;

/** Exclusão da conta do cliente no client-service (porta do domínio, DEV-0105, ADR-012). */
public interface AccountErasure {

    /**
     * Apaga de vez o que é do cliente: perfil, saúde, avaliações e fotos registradas, vínculos e consentimentos, e o
     * e-mail dos convites que ele aceitou ou que foram enviados para {@code email}, em qualquer estado (nulo quando
     * o token não traz e-mail). Guarda o id na lista de contas excluídas. Fica o registro de leitura da saúde, que é obrigação legal
     * por 6 meses, e o lado profissional da mesma conta. Chamado dentro da transação da exclusão.
     */
    void erase(UUID clientId, String email, Instant at);

    /** Tira da lista as contas excluídas antes de {@code cutoff}. Devolve quantas saíram. */
    int purgeDeletedBefore(Instant cutoff);
}
