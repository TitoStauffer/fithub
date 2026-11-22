package com.fithub.commons.logging;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Padrões de logging dos serviços: console em JSON (formato ECS do Spring Boot), com o traceId e o
 * spanId que o Micrometer Tracing põe no MDC. Entram com a menor precedência, então qualquer
 * {@code application.yml}, variável de ambiente ou argumento do serviço sobrescreve.
 */
public class StructuredLoggingDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "fithubStructuredLoggingDefaults";

    static final Map<String, Object> DEFAULTS = Map.of(
            "logging.structured.format.console", "ecs",
            // Sem exportador configurado, amostrar tudo só garante traceId em todas as linhas de log.
            "management.tracing.sampling.probability", "1.0");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}
