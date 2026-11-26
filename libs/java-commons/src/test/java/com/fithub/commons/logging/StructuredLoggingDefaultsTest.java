package com.fithub.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Padrões de logging da java-commons (DEV-0109). O formato da linha (JSON com traceId) é o mesmo no console e no
 * arquivo; o do console já tem teste de ponta a ponta no JavaCommonsIntegrationTest, e o coletor do compose lê o
 * arquivo (infra/observability/observability.test.sh).
 */
class StructuredLoggingDefaultsTest {

    @Test
    void logFileUsesTheSameJsonFormatAsTheConsole() {
        StandardEnvironment environment = new StandardEnvironment();
        new StructuredLoggingDefaults().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.format.file"))
                .isEqualTo(environment.getProperty("logging.structured.format.console"));
    }

    @Test
    void serviceConfigurationStillWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "servico", java.util.Map.of("logging.structured.format.file", "logstash")));
        new StructuredLoggingDefaults().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("logstash");
    }
}
