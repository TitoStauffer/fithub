package com.fithub.client.domain.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exclusão da conta do cliente no client-service (porta do domínio, DEV-0105, ADR-012). */
public interface AccountErasure {

    /**
     * Apaga de vez o que é do cliente: perfil, saúde, avaliações e fotos registradas, vínculos e consentimentos, e o
     * e-mail dos convites que ele aceitou. Guarda o id na lista de contas excluídas, com o hash do e-mail em minúsculas,
     * nunca o e-mail (DEV-0174), e apaga o e-mail dos convites, em qualquer estado, cujo e-mail tem esse hash e que foram
     * criados até a exclusão ({@code at}); um convite posterior, para a mesma pessoa de volta com uma conta nova, fica.
     * {@code email} é nulo quando o token não traz e-mail e na reaplicação depois de restaurar um backup, que usa o hash
     * já guardado. Fica o registro de leitura da saúde, que é obrigação legal por 6 meses, e o lado profissional da
     * mesma conta. Chamado dentro da transação da exclusão.
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
