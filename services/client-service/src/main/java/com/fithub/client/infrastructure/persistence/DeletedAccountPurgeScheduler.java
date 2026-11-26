package com.fithub.client.infrastructure.persistence;

import com.fithub.client.application.DeleteClientAccount;
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
 * Expurgo periódico da lista de contas excluídas (DEV-0105, ADR-012): o id sai depois de 30 dias, quando nenhum
 * backup tem mais o dado. O log traz só a quantidade, nunca o id.
 */
@Component
class DeletedAccountPurgeScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DeletedAccountPurgeScheduler.class);

    private final DeleteClientAccount deleteClientAccount;
    private final Duration interval;
    private ScheduledExecutorService scheduler;

    DeletedAccountPurgeScheduler(
            DeleteClientAccount deleteClientAccount,
            @Value("${fithub.deleted-accounts.purge-interval:PT6H}") Duration interval) {
        this.deleteClientAccount = deleteClientAccount;
        this.interval = interval;
    }

    void purge() {
        int removed = deleteClientAccount.purgeDeletedAccounts();
        if (removed > 0) {
            log.info("Expurgo de contas excluídas: {} id(s) removido(s) da lista", removed);
        }
        // Depois de restaurar um backup, o dado de quem excluiu a conta volta; a exclusão é reaplicada (DEV-0172).
        int reapplied = deleteClientAccount.reapplyDeletions();
        if (reapplied > 0) {
            log.info("Exclusão reaplicada a {} conta(s) da lista", reapplied);
        }
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-deleted-account-purge").factory());
        long period = interval.toMillis();
        // A primeira rodada é na subida: o dado de uma restauração sai antes de o serviço atender por muito tempo.
        scheduler.scheduleWithFixedDelay(this::safely, 0, period, TimeUnit.MILLISECONDS);
    }

    private void safely() {
        try {
            purge();
        } catch (RuntimeException failure) {
            // Banco fora do ar não derruba o serviço; tenta de novo no próximo ciclo.
            log.warn(
                    "Expurgo de contas excluídas falhou ({}); nova tentativa no próximo ciclo",
                    failure.getClass().getSimpleName());
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
