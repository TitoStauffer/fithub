package com.fithub.commons.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Medidores agregados das métricas de sucesso do PRD (DEV-0113). Cada serviço registra uma consulta ao próprio banco
 * por medidor; a consulta roda a cada {@code fithub.metrics.refresh-interval} (padrão 5 min), e a exportação lê só o
 * último valor, sem tocar no banco.
 * <ul>
 * <li><b>Só agregados:</b> contagens com rótulos de valor fixo (tipo de profissional, estado), nunca um id ou um dado
 * que aponte uma pessoa. O registro recusa rótulo fora da lista permitida.</li>
 * <li>Uma consulta que falha mantém o último valor e registra só o nome do medidor.</li>
 * </ul>
 */
public class AggregateGauges implements SmartLifecycle {

    /** Rótulos aceitos: só categorias de valor fixo. */
    static final List<String> ALLOWED_TAGS = List.of("type", "status");

    private static final Logger log = LoggerFactory.getLogger(AggregateGauges.class);

    private record Entry(String name, LongSupplier query, AtomicLong value) {}

    private final MeterRegistry registry;
    private final Duration refreshInterval;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    public AggregateGauges(MeterRegistry registry, Duration refreshInterval) {
        this.registry = registry;
        this.refreshInterval = refreshInterval;
    }

    /**
     * Registra um medidor agregado.
     *
     * @param name        nome da métrica, começando por {@code fithub.}
     * @param description o que a contagem mede
     * @param tags        só rótulos de {@link #ALLOWED_TAGS}
     * @param query       contagem no banco do serviço
     */
    public void register(String name, String description, Tags tags, LongSupplier query) {
        if (name == null || !name.startsWith("fithub.")) {
            throw new IllegalArgumentException("Métrica agregada precisa começar por fithub.: " + name);
        }
        tags.forEach(tag -> {
            if (!ALLOWED_TAGS.contains(tag.getKey())) {
                throw new IllegalArgumentException("Rótulo fora dos permitidos " + ALLOWED_TAGS + ": " + tag.getKey());
            }
        });
        AtomicLong value = new AtomicLong();
        Gauge.builder(name, value, AtomicLong::get)
                .description(description)
                .tags(tags)
                .register(registry);
        entries.add(new Entry(name, query, value));
    }

    /** Recalcula todos os medidores; devolve quantos consultaram com sucesso. */
    public int refresh() {
        int refreshed = 0;
        for (Entry entry : entries) {
            try {
                entry.value().set(entry.query().getAsLong());
                refreshed++;
            } catch (RuntimeException failure) {
                log.warn("Falha ao calcular a métrica {}; fica o último valor", entry.name(), failure);
            }
        }
        return refreshed;
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fithub-aggregate-gauges").factory());
        scheduler.scheduleWithFixedDelay(this::refresh, 0, refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
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
