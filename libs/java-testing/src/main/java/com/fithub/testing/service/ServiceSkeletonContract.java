package com.fithub.testing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
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

    @Autowired
    private Environment environment;

    @Value("${fithub.messaging.exchange:}")
    private String ownExchange;

    @Autowired
    private ApplicationContext context;

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
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearer").isArray());
    }

    @Test
    void openApiMatchesTheContractCommittedForTheApps() throws Exception {
        // DEV-0022: o JSON versionado em packages/api-types/openapi é a fonte dos tipos dos apps.
        String live = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        OpenApiContract.verify(serviceName, live);
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
        assertThat(jdbc.sql("select to_regclass('fithub_outbox_event') is not null")
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    // O serviço só declara a própria exchange: as dos outros vêm do administrador do broker, e o usuário do serviço
    // não tem permissão de configurá-las, o que incluiria apagá-las (DEV-0177).
    @Test
    void theServiceDeclaresNoExchangeOfAnotherService() {
        List<Exchange> exchanges = Stream.concat(
                        context.getBeansOfType(Exchange.class).values().stream(),
                        context.getBeansOfType(Declarables.class).values().stream()
                                .flatMap(declarables -> declarables.getDeclarablesByType(Exchange.class).stream()))
                .toList();
        List<Exchange> others = exchanges.stream()
                .filter(exchange -> !exchange.getName().equals(ownExchange))
                .toList();

        assertThat(others).as("o serviço consome de outro serviço").isNotEmpty();
        assertThat(others)
                .allSatisfy(exchange -> assertThat(exchange.shouldDeclare())
                        .as(exchange.getName())
                        .isFalse());
    }

    // Com o batch do Hibernate, a primeira linha do BatchUpdateException traz o SQL com os valores da linha, e ela
    // chegaria ao log mesmo com o driver sem o Detail (DEV-0183).
    @Test
    void hibernateJdbcBatchingStaysOff() {
        String batchSize = environment.getProperty("spring.jpa.properties.hibernate.jdbc.batch_size");
        assertThat(batchSize == null || Integer.parseInt(batchSize.trim()) <= 1)
                .as("hibernate.jdbc.batch_size = %s", batchSize)
                .isTrue();
    }

    private static List<String> successfulVersions(JdbcClient jdbc, String historyTable) {
        // Nome de tabela fixo deste teste, nunca entrada externa.
        return jdbc.sql("select version from " + historyTable + " where success and version is not null")
                .query(String.class)
                .list();
    }
}
