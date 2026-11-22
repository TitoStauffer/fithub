package com.fithub.commons.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.json.JsonMapper;

/**
 * Grava um evento no outbox dentro da transação do dado (ADR-003). Fora de uma transação a gravação é
 * recusada: sem ela, o evento poderia sair sem o dado ou o dado ficar sem o evento.
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final String exchange;
    private final Clock clock;

    public OutboxWriter(JdbcClient jdbc, JsonMapper jsonMapper, String exchange, Clock clock) {
        if (exchange == null || exchange.isBlank()) throw new IllegalArgumentException("fithub.messaging.exchange é obrigatório para gravar no outbox.");
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.exchange = exchange;
        this.clock = clock;
    }

    /**
     * @param routingKey tipo e versão do evento, por exemplo {@code client.vinculo-ativado.v1}
     * @param payload    conteúdo do evento, serializado em JSON
     * @return o id do evento, que viaja como {@code messageId} e deduplica o consumo
     */
    public UUID append(String routingKey, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("O outbox só grava dentro da transação do dado.");
        }
        if (routingKey == null || routingKey.isBlank()) throw new IllegalArgumentException("routingKey é obrigatória.");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at) values (?, ?, ?, cast(? as jsonb), ?)")
                .params(id, exchange, routingKey, jsonMapper.writeValueAsString(payload), Timestamp.from(clock.instant()))
                .update();
        return id;
    }
}
