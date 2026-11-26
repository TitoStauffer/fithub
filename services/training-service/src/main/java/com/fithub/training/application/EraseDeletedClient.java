package com.fithub.training.application;

import com.fithub.training.domain.account.ClientErasure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * O cliente excluiu a conta (DEV-0162, {@code ContaExcluida}, ADR-012): o training apaga o que é dele e anonimiza as
 * fichas que os personais criaram. Roda dentro da transação do consumo idempotente.
 */
@Service
public class EraseDeletedClient {

    /** Quanto tempo o id fica na lista de contas excluídas: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_ACCOUNT_RETENTION = Duration.ofDays(30);

    private final ClientErasure erasure;
    private final TransactionOperations transaction;
    private final Clock clock;

    public EraseDeletedClient(ClientErasure erasure, TransactionOperations transaction, Clock clock) {
        this.erasure = erasure;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void accountDeleted(UUID clientId, Instant occurredAt) {
        erasure.erase(clientId, occurredAt);
    }

    /**
     * Reaplica a exclusão de cada conta da lista (DEV-0172): depois de restaurar um backup, o dado de quem excluiu a conta
     * depois do backup volta. Só apaga, cada conta na sua transação, sem mudar a data da lista. Devolve quantas contas
     * foram processadas.
     */
    public int reapplyDeletions() {
        // Só depois de uma restauração (marcador da restore.sh): sem ela, o dado de um id da lista é dado novo do
        // titular.
        if (!erasure.restorePending()) {
            return 0;
        }
        List<ClientErasure.DeletedAccount> deleted = erasure.deletedAccounts();
        for (ClientErasure.DeletedAccount account : deleted) {
            transaction.executeWithoutResult(status -> erasure.erase(account.clientId(), account.deletedAt()));
        }
        // Só no fim: se algo falhar no meio, o marcador fica e a reaplicação roda de novo no próximo ciclo.
        erasure.clearRestorePending();
        return deleted.size();
    }

    /** Expurgo da lista de contas excluídas; devolve quantas saíram. */
    public int purgeDeletedAccounts() {
        return erasure.purgeDeletedBefore(clock.instant().minus(DELETED_ACCOUNT_RETENTION));
    }
}
