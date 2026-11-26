package com.fithub.client.application;

import com.fithub.client.domain.account.AccountErasure;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.link.LinkEvents;
import com.fithub.client.domain.link.LinkRepository;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.client.domain.profile.ClientEvents;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * O cliente exclui a própria conta (DEV-0105, NFR-01, ADR-012). Apaga o que é dele no client-service e grava o
 * {@code ContaExcluida} no outbox na mesma transação; os outros serviços apagam o que têm ao recebê-lo. Sempre
 * publica, mesmo sem nada aqui: training e nutrition podem ter dado do cliente sem perfil (ficha própria). Antes de
 * apagar, encerra os vínculos ativos com o {@code VinculoEncerrado}: o acesso dos profissionais nos outros serviços é
 * cortado por ele, sem depender do consumo do {@code ContaExcluida} (Code Review do PR #294).
 */
@Service
public class DeleteClientAccount {

    /** Quanto tempo o id fica na lista de contas excluídas: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_ACCOUNT_RETENTION = Duration.ofDays(30);

    private final AccountErasure erasure;
    private final ClientEvents events;
    private final LinkRepository links;
    private final LinkEvents linkEvents;
    private final ManagePhotos photos;
    private final TransactionOperations transaction;
    private final Clock clock;

    public DeleteClientAccount(
            AccountErasure erasure,
            ClientEvents events,
            LinkRepository links,
            LinkEvents linkEvents,
            ManagePhotos photos,
            TransactionOperations transaction,
            Clock clock) {
        this.erasure = erasure;
        this.events = events;
        this.links = links;
        this.linkEvents = linkEvents;
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
            endActiveLinks(clientId, now);
            erasure.erase(clientId, inviteEmail, now);
            events.deleted(clientId, now);
        });
        // Depois do commit: com a transação desfeita, nenhum arquivo pode ter saído.
        photos.deleteFilesOfClient(clientId);
    }

    /**
     * Encerra pelo cliente cada vínculo ativo e grava o {@code VinculoEncerrado}, como o encerramento comum
     * (DEV-0037, DEV-0038). Se um encerramento simultâneo gravou primeiro, só ele publica.
     */
    private void endActiveLinks(UUID clientId, Instant now) {
        for (ProfessionalType type : ProfessionalType.values()) {
            links.findActive(clientId, type).ifPresent(link -> {
                Link ended = link.end(Link.Party.CLIENT, now);
                if (links.end(ended)) {
                    linkEvents.ended(ended);
                }
            });
        }
    }

    /**
     * Reaplica a exclusão de cada conta da lista (DEV-0172): depois de restaurar um backup, o dado de quem excluiu a conta
     * depois do backup volta. Só apaga, sem evento (os outros serviços reaplicam a sua parte) e sem mudar a data da lista.
     * O e-mail dos convites pendentes não é conhecido aqui e fica. Devolve quantas contas foram processadas.
     */
    public int reapplyDeletions() {
        List<AccountErasure.DeletedAccount> deleted = erasure.deletedAccounts();
        for (AccountErasure.DeletedAccount account : deleted) {
            transaction.executeWithoutResult(status -> erasure.erase(account.clientId(), null, account.deletedAt()));
            photos.deleteFilesOfClient(account.clientId());
        }
        return deleted.size();
    }

    /** Expurgo da lista de contas excluídas; devolve quantas saíram. */
    public int purgeDeletedAccounts() {
        return erasure.purgeDeletedBefore(clock.instant().minus(DELETED_ACCOUNT_RETENTION));
    }
}
