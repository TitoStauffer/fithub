package com.fithub.account.infrastructure.keycloak;

import com.fithub.account.application.RemoveDeletedLogin;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Expurgo periódico da lista de logins apagados (DEV-0173, ADR-012): o id sai depois de 30 dias, quando nenhum backup
 * tem mais o usuário. Depois de uma restauração, reapaga no Keycloak os logins da lista. A primeira rodada é na subida.
 * Rodada que falha (Keycloak ainda subindo depois da restauração, banco fora do ar) é tentada de novo em pouco tempo,
 * não no ciclo de 6 h: enquanto o marcador da restauração fica, o login de quem excluiu a conta volta a funcionar
 * (DEV-0175). O log traz só quantidades, nunca o id.
 */
@Component
class DeletedLoginPurgeScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DeletedLoginPurgeScheduler.class);

    private final RemoveDeletedLogin removeDeletedLogin;
    private final Duration interval;
    private final Duration retryInterval;
    private ScheduledExecutorService scheduler;

    DeletedLoginPurgeScheduler(
            RemoveDeletedLogin removeDeletedLogin,
            @Value("${fithub.deleted-accounts.purge-interval:PT6H}") Duration interval,
            @Value("${fithub.deleted-accounts.retry-interval:PT1M}") Duration retryInterval) {
        this.removeDeletedLogin = removeDeletedLogin;
        this.interval = interval;
        this.retryInterval = retryInterval;
    }

    void purge() {
        int removed = removeDeletedLogin.purgeDeletedLogins();
        if (removed > 0) {
            log.info("Expurgo de logins apagados: {} id(s) removido(s) da lista", removed);
        }
        int reapplied = removeDeletedLogin.reapplyDeletions();
        if (reapplied > 0) {
            log.info("Login reapagado no Keycloak para {} conta(s) da lista", reapplied);
        }
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-deleted-login-purge").factory());
        scheduler.execute(this::cycle);
    }

    private void cycle() {
        Duration next = runOnce();
        synchronized (this) {
            // Depois do stop, não agenda mais nada.
            if (scheduler != null) {
                scheduler.schedule(this::cycle, next.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Uma rodada; devolve quanto esperar até a próxima. */
    Duration runOnce() {
        try {
            purge();
            return interval;
        } catch (RuntimeException failure) {
            // Banco ou Keycloak fora do ar não derruba o serviço; tenta de novo em pouco tempo (DEV-0175).
            log.warn(
                    "Expurgo de logins apagados falhou ({}); nova tentativa em {}",
                    failure.getClass().getSimpleName(),
                    retryInterval);
            return retryInterval;
        }
    }

    @Override
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return scheduler != null;
    }
}
