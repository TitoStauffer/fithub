package com.fithub.commons.messaging;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Padrões da mensageria, com a menor precedência:
 * <ul>
 * <li>confirmações simples no canal: o {@link OutboxPublisher} só marca um evento como publicado depois
 * da confirmação do broker;</li>
 * <li>linha de base do Flyway do serviço na versão 0: as tabelas técnicas da biblioteca
 * ({@link FithubCommonsMigrations}) deixam o schema não vazio, e sem isso o Flyway do serviço recusaria
 * migrar; com a base em 0, a V1 do serviço ainda roda.</li>
 * <li>observação dos listeners do RabbitMQ (DEV-0109): o consumidor lê o cabeçalho {@code traceparent}
 * que o {@link OutboxPublisher} manda e continua o trace da requisição que gravou o evento.</li>
 * </ul>
 */
public class MessagingDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "fithubMessagingDefaults";

    static final Map<String, Object> DEFAULTS = Map.of(
            "spring.rabbitmq.publisher-confirm-type", "simple",
            "spring.flyway.baseline-on-migrate", "true",
            "spring.flyway.baseline-version", "0",
            "spring.rabbitmq.listener.simple.observation-enabled", "true");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}
