package com.fithub.nutrition.domain.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exclusão do cliente que excluiu a conta, no nutrition-service (porta do domínio, DEV-0163, ADR-012). */
public interface ClientErasure {

    /**
     * Apaga de vez o que é do cliente: registros de refeição (com trocas, desvios e a referência à foto do prato),
     * restrições alimentares e as projeções do cliente e dos vínculos. Os planos alimentares das nutricionistas ficam
     * com elas, com o id do cliente trocado por um id aleatório. Guarda o id na lista de contas excluídas. Chamado
     * dentro da transação do consumo.
     */
    void erase(UUID clientId, Instant at);

    /** Tira da lista as contas excluídas antes de {@code cutoff}. Devolve quantas saíram. */
    int purgeDeletedBefore(Instant cutoff);

    /** Uma conta da lista de contas excluídas, com a data da exclusão. */
    record DeletedAccount(UUID clientId, Instant deletedAt) {}

    /** A lista de contas excluídas, da mais antiga para a mais nova (DEV-0172). */
    List<DeletedAccount> deletedAccounts();
}
