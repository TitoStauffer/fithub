package com.fithub.nutrition.infrastructure.storage;

import com.fithub.nutrition.application.MealLogPhotos;
import java.time.Clock;
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
 * Expurgo periódico dos envios de foto do prato que nunca foram confirmados (DEV-0088), como no client-service (DEV-0048).
 * Depois de {@code fithub.storage.pending-max-age} sem confirmação, o envio sai do bucket. O log traz só a quantidade,
 * nunca chave nem URL.
 */
@Component
class MealPhotoPurgeScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MealPhotoPurgeScheduler.class);

    private final MealLogPhotos photos;
    private final Clock clock;
    private final Duration interval;
    private final Duration pendingMaxAge;
    private ScheduledExecutorService scheduler;

    MealPhotoPurgeScheduler(
            MealLogPhotos photos,
            Clock clock,
            @Value("${fithub.storage.purge-interval:PT1H}") Duration interval,
            @Value("${fithub.storage.pending-max-age:PT1H}") Duration pendingMaxAge) {
        this.photos = photos;
        this.clock = clock;
        this.interval = interval;
        this.pendingMaxAge = pendingMaxAge;
    }

    void purge() {
        int removed = photos.purgeUnconfirmed(clock.instant().minus(pendingMaxAge));
        if (removed > 0) {
            log.info("Expurgo das fotos do prato: {} envio(s) não confirmado(s) removido(s) do bucket", removed);
        }
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-meal-photo-purge").factory());
        long period = interval.toMillis();
        scheduler.scheduleWithFixedDelay(this::safely, period, period, TimeUnit.MILLISECONDS);
    }

    private void safely() {
        try {
            purge();
        } catch (RuntimeException failure) {
            // Armazenamento fora do ar não derruba o serviço; tenta de novo no próximo ciclo. Só o tipo da falha vai
            // para o log: a mensagem do S3 pode trazer a chave do objeto.
            log.warn(
                    "Expurgo das fotos do prato falhou ({}); nova tentativa no próximo ciclo",
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
