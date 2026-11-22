package com.fithub.testing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O que todo serviço entrega desde o esqueleto (DEV-0015). Cada serviço tem uma subclasse vazia:
 *
 * <pre>{@code
 * class AccountServiceSkeletonTest extends ServiceSkeletonContract {
 * }
 * }</pre>
 */
public abstract class ServiceSkeletonContract extends ServiceIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Value("${spring.application.name}")
    private String serviceName;

    @Test
    void healthIsPublicAndUpWithDatabaseAndBroker() throws Exception {
        // O status agregado só fica UP com os indicadores do Postgres e do RabbitMQ saudáveis.
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiIsPublicAndDescribesTheServiceAndTheBearerToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(serviceName))
                .andExpect(jsonPath("$.components.securitySchemes.bearer.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearer").isArray());
    }

    @Test
    void everyOtherPathRequiresATokenAndAnswersWithProblemDetails() throws Exception {
        // Vem da java-commons: o serviço não abre nada além de health e OpenAPI.
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void serviceAndCommonsMigrationsAreApplied() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(successfulVersions(jdbc, "flyway_schema_history")).contains("1");
        assertThat(successfulVersions(jdbc, "flyway_fithub_commons_history")).contains("1");
        assertThat(jdbc.sql("select to_regclass('fithub_outbox_event') is not null").query(Boolean.class).single())
                .isTrue();
    }

    private static List<String> successfulVersions(JdbcClient jdbc, String historyTable) {
        // Nome de tabela fixo deste teste, nunca entrada externa.
        return jdbc.sql("select version from " + historyTable + " where success and version is not null")
                .query(String.class).list();
    }
}
