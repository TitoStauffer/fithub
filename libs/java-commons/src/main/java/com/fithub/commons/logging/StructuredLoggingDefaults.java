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
            // O arquivo de log, quando o serviço liga logging.file.name, no mesmo JSON: o coletor do compose lê
            // esse arquivo e manda as linhas para o Loki (DEV-0109).
            "logging.structured.format.file", "ecs",
            // Amostrar tudo garante traceId em todas as linhas de log e, com o exportador OTLP ligado, todos os
            // traces no Tempo (o volume do MVP é pequeno; o serviço pode baixar a probabilidade).
            "management.tracing.sampling.probability", "1.0",
            // Sem as observações do Spring Security (DEV-0109): elas criam um span por filtro, com os papéis da pessoa
            // (spring.security.authentication.authorities). O span da requisição continua, com rota, status e duração.
            "management.observations.enable.spring.security", "false");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}
