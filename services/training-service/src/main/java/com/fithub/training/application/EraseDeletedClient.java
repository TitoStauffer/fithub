package com.fithub.training.application;

import com.fithub.training.domain.account.ClientErasure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * O cliente excluiu a conta (DEV-0162, {@code ContaExcluida}, ADR-012): o training apaga o que é dele e anonimiza as
 * fichas que os personais criaram. Roda dentro da transação do consumo idempotente.
 */
@Service
public class EraseDeletedClient {

    /** Quanto tempo o id fica na lista de contas excluídas: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_ACCOUNT_RETENTION = Duration.ofDays(30);

    private final ClientErasure erasure;
    private final Clock clock;

    public EraseDeletedClient(ClientErasure erasure, Clock clock) {
        this.erasure = erasure;
        this.clock = clock;
    }

    public void accountDeleted(UUID clientId, Instant occurredAt) {
        erasure.erase(clientId, occurredAt);
    }

    /** Expurgo da lista de contas excluídas; devolve quantas saíram. */
    public int purgeDeletedAccounts() {
        return erasure.purgeDeletedBefore(clock.instant().minus(DELETED_ACCOUNT_RETENTION));
    }
}
