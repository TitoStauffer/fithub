package com.fithub.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exportação dos spans por OTLP (DEV-0109, ADR-013), com a mesma autoconfiguração que os serviços recebem
 * da java-commons. Um servidor HTTP local faz o papel do coletor.
 */
class TraceExportTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetrySdkAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    OtlpTracingAutoConfiguration.class))
            .withPropertyValues(
                    "spring.application.name=servico-de-teste", "management.tracing.sampling.probability=1.0");

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
    void withTheEndpointTheSpansGoToTheCollector() {
        String endpoint = "http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/traces";
        contextRunner
                .withPropertyValues("management.opentelemetry.tracing.export.otlp.endpoint=" + endpoint)
                .run(context -> {
                    assertThat(context).hasSingleBean(SpanExporter.class);
                    endSpanAndFlush(context.getBean(SdkTracerProvider.class));

                    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("POST /v1/traces");
                });
    }

    @Test
    void withoutTheEndpointNothingIsExported() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SpanExporter.class);
            endSpanAndFlush(context.getBean(SdkTracerProvider.class));

            assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
        });
    }

    private static void endSpanAndFlush(SdkTracerProvider provider) {
        provider.get("teste").spanBuilder("operacao-de-teste").startSpan().end();
        provider.forceFlush().join(10, TimeUnit.SECONDS);
    }
}
