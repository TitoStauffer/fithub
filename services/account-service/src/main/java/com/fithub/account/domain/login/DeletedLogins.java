package com.fithub.account.domain.login;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lista dos logins apagados porque o cliente excluiu a conta (porta do domínio, DEV-0173, ADR-012, item 4). Depois de
 * restaurar um backup, o Keycloak volta com o usuário de quem excluiu a conta depois do backup, e o account-service
 * reapaga cada login desta lista.
 */
public interface DeletedLogins {

    /** Grava o id na lista; repetir mantém uma linha só, com a data mais recente. */
    void record(UUID accountId, Instant deletedAt);

    /** Os ids da lista. */
    List<UUID> accountIds();

    /** Tira da lista quem foi apagado antes do corte; devolve quantos saíram. */
    int purgeDeletedBefore(Instant cutoff);

    /** Se a infra/backup/restore.sh restaurou o banco e a reaplicação ainda não rodou (DEV-0172). */
    boolean restorePending();

    /** A reaplicação terminou: o marcador de restauração sai. */
    void clearRestorePending();
}
