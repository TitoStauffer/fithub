package com.fithub.account.application;

import com.fithub.account.domain.login.DeletedLogins;
import com.fithub.account.domain.login.LoginAccounts;
import com.fithub.account.domain.professional.ProfessionalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * O cliente excluiu a conta (DEV-0161, {@code ContaExcluida}, ADR-012): o login sai do Keycloak e o id entra na lista
 * de logins apagados (DEV-0173). Se a mesma conta também é de profissional, o login fica, porque o lado profissional
 * continua (a exclusão pelo profissional está fora do MVP), e o id não entra na lista. O papel {@code client} vem do
 * papel padrão do realm e fica com ela.
 */
@Service
public class RemoveDeletedLogin {

    /** Quanto tempo o id fica na lista: o mesmo dos backups (ADR-012, item 4). */
    static final Duration DELETED_LOGIN_RETENTION = Duration.ofDays(30);

    private final ProfessionalRepository professionals;
    private final LoginAccounts logins;
    private final DeletedLogins deletedLogins;
    private final Clock clock;

    public RemoveDeletedLogin(
            ProfessionalRepository professionals, LoginAccounts logins, DeletedLogins deletedLogins, Clock clock) {
        this.professionals = professionals;
        this.logins = logins;
        this.deletedLogins = deletedLogins;
        this.clock = clock;
    }

    /** Devolve se o login foi apagado ({@code false} quando a conta também é de profissional). */
    public boolean accountDeleted(UUID clientId, Instant occurredAt) {
        if (professionals.findByAccountId(clientId.toString()).isPresent()) {
            return false;
        }
        logins.delete(clientId);
        deletedLogins.record(clientId, occurredAt);
        return true;
    }

    /**
     * Reapaga no Keycloak cada login da lista (DEV-0173), só depois de uma restauração (marcador da restore.sh). O
     * usuário já ausente conta como apagado, então repetir é seguro. Devolve quantos logins foram processados.
     */
    public int reapplyDeletions() {
        if (!deletedLogins.restorePending()) {
            return 0;
        }
        List<UUID> accountIds = deletedLogins.accountIds();
        for (UUID accountId : accountIds) {
            logins.delete(accountId);
        }
        // Só no fim: se o Keycloak falhar no meio, o marcador fica e a reaplicação roda de novo no próximo ciclo.
        deletedLogins.clearRestorePending();
        return accountIds.size();
    }

    /** Expurgo da lista de logins apagados; devolve quantos saíram. */
    public int purgeDeletedLogins() {
        return deletedLogins.purgeDeletedBefore(clock.instant().minus(DELETED_LOGIN_RETENTION));
    }
}
