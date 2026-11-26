package com.fithub.training.infrastructure.persistence;

import com.fithub.training.application.AccessLogRetention;
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
 * Expurgo periódico do registro de leituras de dados sensíveis pelo personal (DEV-0170, ADR-012, item 6): sai o
 * que passou de 6 meses. O log traz só a quantidade, nunca ids.
 */
@Component
class AccessLogPurgeScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AccessLogPurgeScheduler.class);

    private final AccessLogRetention retention;
    private final Duration interval;
    private ScheduledExecutorService scheduler;

    AccessLogPurgeScheduler(
            AccessLogRetention retention, @Value("${fithub.access-log.purge-interval:PT6H}") Duration interval) {
        this.retention = retention;
        this.interval = interval;
    }

    void purge() {
        int removed = retention.purge();
        if (removed > 0) {
            log.info("Expurgo do registro de acessos: {} registro(s) com mais de 6 meses removido(s)", removed);
        }
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-access-log-purge").factory());
        long period = interval.toMillis();
        scheduler.scheduleWithFixedDelay(this::safely, period, period, TimeUnit.MILLISECONDS);
    }

    private void safely() {
        try {
            purge();
        } catch (RuntimeException failure) {
            // Banco fora do ar não derruba o serviço; tenta de novo no próximo ciclo.
            log.warn(
                    "Expurgo do registro de acessos falhou ({}); nova tentativa no próximo ciclo",
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
