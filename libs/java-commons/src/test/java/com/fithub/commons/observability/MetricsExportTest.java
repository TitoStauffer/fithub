package com.fithub.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.logging.StructuredLoggingDefaults;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exportação das métricas por OTLP (DEV-0113), com os padrões da java-commons aplicados como nos serviços. Um servidor
 * HTTP local faz o papel do coletor.
 */
class MetricsExportTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> new StructuredLoggingDefaults()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
            .withConfiguration(
                    AutoConfigurations.of(MetricsAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class));

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private HttpServer collector;

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                body.readAllBytes();
            }
            received.add(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        collector.start();
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void withoutTurningItOnNothingIsExported() {
        contextRunner
                .withPropertyValues(
                        "management.otlp.metrics.export.url=" + url(), "management.otlp.metrics.export.step=1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);

                    assertThat(received.poll(3, TimeUnit.SECONDS)).isNull();
                });
    }

    @Test
    void turnedOnTheMetricsGoToTheCollector() {
        contextRunner
                .withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=" + url(),
                        "management.otlp.metrics.export.step=1s")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
                    context.getBean(MeterRegistry.class).gauge("fithub.teste", 1);

                    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("POST /v1/metrics");
                });
    }

    private String url() {
        return "http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/metrics";
    }
}
