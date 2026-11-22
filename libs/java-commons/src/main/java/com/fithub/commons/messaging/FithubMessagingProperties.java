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
        publisher = publisher == null ? new Publisher(null, null, null, null, null, null, null) : publisher;
    }

    /**
     * @param enabled            liga o publicador agendado (padrão {@code true})
     * @param pollInterval       intervalo entre ciclos de publicação (padrão 1s)
     * @param batchSize          eventos por ciclo (padrão 100)
     * @param confirmTimeout     espera pela confirmação do broker (padrão 5s)
     * @param outboxRetention    quanto tempo um evento já publicado fica no outbox (padrão 7 dias)
     * @param processedRetention quanto tempo um evento consumido fica registrado para deduplicação
     *                           (padrão 30 dias; precisa cobrir o maior atraso de reentrega)
     * @param purgeInterval      intervalo entre expurgos (padrão 1h)
     */
    public record Publisher(Boolean enabled, Duration pollInterval, Integer batchSize, Duration confirmTimeout,
            Duration outboxRetention, Duration processedRetention, Duration purgeInterval) {
        public Publisher {
            enabled = enabled == null ? Boolean.TRUE : enabled;
            pollInterval = pollInterval == null ? Duration.ofSeconds(1) : pollInterval;
            batchSize = batchSize == null ? 100 : batchSize;
            confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
            outboxRetention = outboxRetention == null ? Duration.ofDays(7) : outboxRetention;
            processedRetention = processedRetention == null ? Duration.ofDays(30) : processedRetention;
            purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
        }
    }
}
