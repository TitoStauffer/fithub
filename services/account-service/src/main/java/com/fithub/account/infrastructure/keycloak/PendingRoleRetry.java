package com.fithub.account.infrastructure.keycloak;

import com.fithub.account.application.RegisterProfessional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tenta de novo, de tempos em tempos, a atribuição dos papéis que ficaram pendentes porque o Keycloak
 * não respondeu no cadastro (DEV-0029). Desligável por {@code fithub.professional.role-retry.enabled}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fithub.professional.role-retry.enabled", matchIfMissing = true)
class PendingRoleRetry {

    private static final int BATCH = 50;

    private final RegisterProfessional registerProfessional;

    PendingRoleRetry(RegisterProfessional registerProfessional) {
        this.registerProfessional = registerProfessional;
    }

    @Scheduled(
            initialDelayString = "${fithub.professional.role-retry.interval:30s}",
            fixedDelayString = "${fithub.professional.role-retry.interval:30s}")
    void retry() {
        registerProfessional.assignPendingRoles(BATCH);
    }
}
