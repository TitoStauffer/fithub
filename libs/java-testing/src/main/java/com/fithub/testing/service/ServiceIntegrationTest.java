package com.fithub.testing.service;

import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Base dos testes de integração de um serviço: sobe o contexto inteiro contra Postgres e RabbitMQ reais,
 * nas mesmas versões do compose de desenvolvimento (DEV-0012).
 *
 * <p>Os contêineres são únicos por JVM e iniciados uma vez: todas as classes de teste do serviço
 * reaproveitam o mesmo contexto do Spring, cujas portas continuam válidas. O Ryuk do Testcontainers os
 * remove quando a JVM termina.
 *
 * <p>O issuer do Keycloak aponta para um endereço inexistente: o decodificador do Spring só o consulta
 * ao validar um token, e testes que precisam de usuário autenticado declaram o próprio
 * {@code JwtDecoder}.
 *
 * <p>Como em produção, o administrador do broker declara as exchanges dos quatro serviços, e o serviço não declara as
 * dos outros ({@code fithub.messaging.declare-source-exchanges=false}, DEV-0177).
 */
@SpringBootTest(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/realms/teste",
            "fithub.messaging.declare-source-exchanges=false"
        })
@AutoConfigureMockMvc
public abstract class ServiceIntegrationTest {

    public static final String POSTGRES_IMAGE = "postgres:17.12";
    public static final String RABBITMQ_IMAGE = "rabbitmq:4.3.8-management";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @ServiceConnection
    protected static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(RABBITMQ_IMAGE);

    /** As exchanges {@code topic} dos serviços, que o administrador do broker declara em produção. */
    public static final List<String> EXCHANGES =
            List.of("fithub.account", "fithub.client", "fithub.training", "fithub.nutrition");

    static {
        POSTGRES.start();
        RABBITMQ.start();
        for (String exchange : EXCHANGES) {
            declareExchange(exchange);
        }
    }

    private static void declareExchange(String name) {
        try {
            var result = RABBITMQ.execInContainer(
                    "rabbitmqadmin", "--non-interactive", "declare", "exchange", "--name", name, "--type", "topic");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("O RabbitMQ de teste não declarou " + name + ": " + result.getStderr());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
