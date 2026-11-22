package com.fithub.commons.messaging;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumo idempotente (ADR-003): registra o evento como processado e roda o handler na mesma
 * transação. Evento repetido não roda o handler de novo; se o handler falha, o registro também volta
 * e uma nova entrega processa o evento. Duas entregas simultâneas do mesmo evento se serializam pela
 * chave primária: a segunda espera a primeira e, depois do commit dela, é ignorada.
 */
public class ProcessedEvents {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public ProcessedEvents(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /** @return {@code true} se o handler rodou; {@code false} se o evento já tinha sido processado. */
    public boolean processOnce(String consumer, UUID eventId, Runnable handler) {
        if (consumer == null || consumer.isBlank()) throw new IllegalArgumentException("consumer é obrigatório.");
        if (eventId == null) throw new IllegalArgumentException("eventId é obrigatório.");
        Boolean processed = transaction.execute(status -> {
            int inserted = jdbc.sql("insert into fithub_processed_event (consumer, event_id, processed_at) values (?, ?, now()) on conflict do nothing")
                    .params(consumer, eventId)
                    .update();
            if (inserted == 0) return false;
            handler.run();
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }

    /** Usa o {@code messageId} gravado pelo outbox; mensagem sem id válido é rejeitada sem reentrega. */
    public boolean processOnce(String consumer, Message message, Runnable handler) {
        String messageId = message.getMessageProperties().getMessageId();
        UUID eventId;
        try {
            eventId = UUID.fromString(messageId);
        } catch (RuntimeException exception) {
            throw new AmqpRejectAndDontRequeueException("Mensagem sem messageId de evento válido.", exception);
        }
        return processOnce(consumer, eventId, handler);
    }
}
