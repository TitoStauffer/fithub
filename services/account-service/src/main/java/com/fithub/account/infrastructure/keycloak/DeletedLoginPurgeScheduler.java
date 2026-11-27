package com.fithub.account.infrastructure.keycloak;

import com.fithub.account.application.RemoveDeletedLogin;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
    private ScheduledThreadPoolExecutor scheduler;

    DeletedLoginPurgeScheduler(
            RemoveDeletedLogin removeDeletedLogin,
            @Value("${fithub.deleted-accounts.purge-interval:PT6H}") Duration interval,
            @Value("${fithub.deleted-accounts.retry-interval:PT1M}") Duration retryInterval) {
        this.removeDeletedLogin = removeDeletedLogin;
        // Zero ou negativo faria a rodada repetir sem espera (o schedule aceita), enchendo o log com o Keycloak fora.
        this.interval = positive("fithub.deleted-accounts.purge-interval", interval);
        this.retryInterval = positive("fithub.deleted-accounts.retry-interval", retryInterval);
    }

    private static Duration positive(String property, Duration value) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " precisa ser positivo: " + value);
        }
        return value;
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
        ScheduledThreadPoolExecutor owner = new ScheduledThreadPoolExecutor(
                1, Thread.ofVirtual().name("fithub-deleted-login-purge").factory());
        scheduler = owner;
        owner.execute(() -> cycle(owner));
    }

    /**
     * Uma rodada e o agendamento da próxima, no mesmo executor. Depois do stop, nada mais é agendado; e uma rodada que
     * ainda terminava quando o serviço parou e voltou não abre uma segunda cadeia no executor novo.
     */
    private void cycle(ScheduledThreadPoolExecutor owner) {
        Duration next = runOnce();
        synchronized (this) {
            if (scheduler == owner) {
                owner.schedule(() -> cycle(owner), next.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Quantas rodadas estão agendadas (para o teste: uma cadeia só). */
    synchronized int scheduledRuns() {
        return scheduler == null ? 0 : scheduler.getQueue().size();
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
