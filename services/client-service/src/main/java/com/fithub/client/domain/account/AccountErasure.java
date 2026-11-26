package com.fithub.client.domain.account;

import java.time.Instant;
import java.util.List;
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

    /** Uma conta da lista de contas excluídas, com a data da exclusão. */
    record DeletedAccount(UUID clientId, Instant deletedAt) {}

    /** A lista de contas excluídas, da mais antiga para a mais nova (DEV-0172). */
    List<DeletedAccount> deletedAccounts();

    /** Se a infra/backup/restore.sh restaurou o banco e a reaplicação ainda não rodou (DEV-0172). */
    boolean restorePending();

    /** A reaplicação terminou: o marcador de restauração sai. */
    void clearRestorePending();
}
