package com.fithub.client.application;

import com.fithub.client.domain.account.AccountErasure;
import com.fithub.client.domain.profile.ClientEvents;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * O cliente exclui a própria conta (DEV-0105, NFR-01, ADR-012). Apaga o que é dele no client-service e grava o
 * {@code ContaExcluida} no outbox na mesma transação; os outros serviços apagam o que têm ao recebê-lo. Sempre
 * publica, mesmo sem nada aqui: training e nutrition podem ter dado do cliente sem perfil (ficha própria).
 */
@Service
public class DeleteClientAccount {

    /** Quanto tempo o id fica na lista de contas excluídas: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_ACCOUNT_RETENTION = Duration.ofDays(30);

    private final AccountErasure erasure;
    private final ClientEvents events;
    private final ManagePhotos photos;
    private final TransactionOperations transaction;
    private final Clock clock;

    public DeleteClientAccount(
            AccountErasure erasure,
            ClientEvents events,
            ManagePhotos photos,
            TransactionOperations transaction,
            Clock clock) {
        this.erasure = erasure;
        this.events = events;
        this.photos = photos;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** {@code clientAccount} vem sempre do token; {@code email} também, e pode faltar. */
    public void delete(String clientAccount, String email) {
        UUID clientId = UUID.fromString(clientAccount);
        Instant now = clock.instant();
        String inviteEmail = email == null || email.isBlank() ? null : email;
        transaction.executeWithoutResult(status -> {
            erasure.erase(clientId, inviteEmail, now);
            events.deleted(clientId, now);
        });
        // Depois do commit: com a transação desfeita, nenhum arquivo pode ter saído.
        photos.deleteFilesOfClient(clientId);
    }

    /** Expurgo da lista de contas excluídas; devolve quantas saíram. */
    public int purgeDeletedAccounts() {
        return erasure.purgeDeletedBefore(clock.instant().minus(DELETED_ACCOUNT_RETENTION));
    }
}
