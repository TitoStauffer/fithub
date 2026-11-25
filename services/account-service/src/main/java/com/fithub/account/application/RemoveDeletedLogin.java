package com.fithub.account.application;

import com.fithub.account.domain.login.LoginAccounts;
import com.fithub.account.domain.professional.ProfessionalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * O cliente excluiu a conta (DEV-0161, {@code ContaExcluida}, ADR-012): o login sai do Keycloak. Se a mesma conta
 * também é de profissional, o login fica, porque o lado profissional continua (a exclusão pelo profissional está fora
 * do MVP). O papel {@code client} vem do papel padrão do realm e fica com ela.
 */
@Service
public class RemoveDeletedLogin {

    private final ProfessionalRepository professionals;
    private final LoginAccounts logins;

    public RemoveDeletedLogin(ProfessionalRepository professionals, LoginAccounts logins) {
        this.professionals = professionals;
        this.logins = logins;
    }

    /** Devolve se o login foi apagado ({@code false} quando a conta também é de profissional). */
    public boolean accountDeleted(UUID clientId) {
        if (professionals.findByAccountId(clientId.toString()).isPresent()) {
            return false;
        }
        logins.delete(clientId);
        return true;
    }
}
