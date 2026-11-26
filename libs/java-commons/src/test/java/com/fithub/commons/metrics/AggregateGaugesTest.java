package com.fithub.commons.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Medidores agregados das métricas de sucesso (DEV-0113). */
class AggregateGaugesTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AggregateGauges gauges = new AggregateGauges(registry, Duration.ofMinutes(5));

    @Test
    void theExportedValueIsTheLastRefreshNotANewQuery() {
        AtomicLong database = new AtomicLong(3);
        AtomicLong queries = new AtomicLong();
        gauges.register("fithub.teste", "contagem de teste", Tags.of("type", "PERSONAL"), () -> {
            queries.incrementAndGet();
            return database.get();
        });

        assertThat(gauges.refresh()).isEqualTo(1);
        database.set(7);
        double exported =
                registry.get("fithub.teste").tag("type", "PERSONAL").gauge().value();

        assertThat(exported).isEqualTo(3);
        assertThat(queries).hasValue(1);
        gauges.refresh();
        assertThat(registry.get("fithub.teste").gauge().value()).isEqualTo(7);
    }

    @Test
    void aFailedQueryKeepsTheLastValue() {
        AtomicLong calls = new AtomicLong();
        gauges.register("fithub.teste", "contagem de teste", Tags.empty(), () -> {
            if (calls.incrementAndGet() > 1) throw new IllegalStateException("banco fora do ar");
            return 5;
        });

        gauges.refresh();
        assertThat(gauges.refresh()).isZero();

        assertThat(registry.get("fithub.teste").gauge().value()).isEqualTo(5);
    }

    @Test
    void aTagThatCouldPointToAPersonIsRefused() {
        assertThatThrownBy(() -> gauges.register("fithub.teste", "x", Tags.of("professionalId", "abc"), () -> 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("professionalId");
        assertThat(registry.find("fithub.teste").gauge()).isNull();
    }

    @Test
    void onlyFithubMetricsAreRegistered() {
        assertThatThrownBy(() -> gauges.register("jvm.qualquer", "x", Tags.empty(), () -> 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startRefreshesRightAwayAndStopEnds() throws Exception {
        gauges.register("fithub.teste", "contagem de teste", Tags.empty(), () -> 9);

        gauges.start();
        try {
            for (int attempt = 0;
                    attempt < 50 && registry.get("fithub.teste").gauge().value() != 9;
                    attempt++) {
                Thread.sleep(20);
            }
            assertThat(registry.get("fithub.teste").gauge().value()).isEqualTo(9);
            assertThat(gauges.isRunning()).isTrue();
        } finally {
            gauges.stop();
        }
        assertThat(gauges.isRunning()).isFalse();
    }
}
