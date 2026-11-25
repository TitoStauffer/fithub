package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.account.ClientErasure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * O cliente excluiu a conta (DEV-0163, {@code ContaExcluida}, ADR-012): o nutrition apaga o que é dele e anonimiza os
 * planos que as nutricionistas criaram. Roda dentro da transação do consumo idempotente; as fotos do prato saem do
 * bucket depois do commit, para nenhum arquivo sair se a transação for desfeita.
 */
@Service
public class EraseDeletedClient {

    /** Quanto tempo o id fica na lista de contas excluídas: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_ACCOUNT_RETENTION = Duration.ofDays(30);

    private final ClientErasure erasure;
    private final MealLogPhotos photos;
    private final Clock clock;

    public EraseDeletedClient(ClientErasure erasure, MealLogPhotos photos, Clock clock) {
        this.erasure = erasure;
        this.photos = photos;
        this.clock = clock;
    }

    public void accountDeleted(UUID clientId, Instant occurredAt) {
        erasure.erase(clientId, occurredAt);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photos.deleteFilesOfClient(clientId);
            }
        });
    }

    /** Expurgo da lista de contas excluídas; devolve quantas saíram. */
    public int purgeDeletedAccounts() {
        return erasure.purgeDeletedBefore(clock.instant().minus(DELETED_ACCOUNT_RETENTION));
    }
}
