package com.fithub.commons.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mensageria comum dos serviços ({@code fithub.messaging.*}).
 *
 * @param exchange  exchange {@code topic} do serviço de origem, por exemplo {@code fithub.client} (ADR-003);
 *                  sem ele o serviço só consome, não grava no outbox
 * @param publisher publicador do outbox
 */
@ConfigurationProperties("fithub.messaging")
public record FithubMessagingProperties(String exchange, Publisher publisher) {

    public FithubMessagingProperties {
        publisher = publisher == null ? new Publisher(null, null, null, null) : publisher;
    }

    /**
     * @param enabled        liga o publicador agendado (padrão {@code true})
     * @param pollInterval   intervalo entre ciclos (padrão 1s)
     * @param batchSize      eventos por ciclo (padrão 100)
     * @param confirmTimeout espera pela confirmação do broker (padrão 5s)
     */
    public record Publisher(Boolean enabled, Duration pollInterval, Integer batchSize, Duration confirmTimeout) {
        public Publisher {
            enabled = enabled == null ? Boolean.TRUE : enabled;
            pollInterval = pollInterval == null ? Duration.ofSeconds(1) : pollInterval;
            batchSize = batchSize == null ? 100 : batchSize;
            confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
        }
    }
}
