package com.fithub.commons.messaging;

import com.fithub.commons.ids.UuidV7;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
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
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("fithub.messaging.exchange é obrigatório para gravar no outbox.");
        }
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
        UUID id = UuidV7.next(clock);
        jdbc.sql("insert into fithub_outbox_event (id, exchange, routing_key, payload, created_at, trace_parent)"
                        + " values (?, ?, ?, cast(? as jsonb), ?, ?)")
                .params(
                        id,
                        exchange,
                        routingKey,
                        jsonMapper.writeValueAsString(payload),
                        Timestamp.from(clock.instant()),
                        currentTraceParent())
                .update();
        return id;
    }

    /**
     * O traceparent W3C do span atual (DEV-0109), para o consumidor continuar o trace da requisição que
     * gravou o evento; nulo quando não há trace. Só identificadores aleatórios do trace.
     */
    static String currentTraceParent() {
        Map<String, String> carrier = new HashMap<>(2);
        W3CTraceContextPropagator.getInstance()
                .inject(Context.current(), carrier, (target, key, value) -> target.put(key, value));
        return carrier.get(OutboxPublisher.TRACE_PARENT_HEADER);
    }
}
