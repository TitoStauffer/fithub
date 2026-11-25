package com.fithub.nutrition.infrastructure.messaging;

import com.fithub.commons.messaging.ProcessedEvents;
import com.fithub.nutrition.application.EraseDeletedClient;
import com.fithub.nutrition.application.ProjectClientEvents;
import com.fithub.nutrition.domain.access.ClientDirectory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor dos eventos do client-service (DEV-0151): {@code VinculoAtivado}, {@code VinculoEncerrado} e
 * {@code ClienteAtualizado}, e o {@code ContaExcluida} (DEV-0163), que apaga o cliente. Os eventos do mesmo cliente
 * chegam na ordem do outbox (publicador único): o {@code VinculoEncerrado} da exclusão vem antes do
 * {@code ContaExcluida}. Fila durável própria, consumo idempotente pela {@code java-commons} e fila de
 * mensagens mortas para payload fora do contrato ou falha depois das tentativas. Nada do payload vai para log.
 */
@Configuration(proxyBeanMethods = false)
class ClientEventsListener {

    static final String EXCHANGE = "fithub.client";
    static final String LINK_ACTIVATED = "client.link-activated.v1";
    static final String LINK_ENDED = "client.link-ended.v1";
    static final String CLIENT_UPDATED = "client.client-updated.v1";
    static final String ACCOUNT_DELETED = "client.account-deleted.v1";
    static final String QUEUE = "nutrition.client-events";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    private final ProjectClientEvents projectClientEvents;
    private final EraseDeletedClient eraseDeletedClient;
    private final ProcessedEvents processedEvents;
    private final JsonMapper jsonMapper;

    ClientEventsListener(
            ProjectClientEvents projectClientEvents,
            EraseDeletedClient eraseDeletedClient,
            ProcessedEvents processedEvents,
            JsonMapper jsonMapper) {
        this.projectClientEvents = projectClientEvents;
        this.eraseDeletedClient = eraseDeletedClient;
        this.processedEvents = processedEvents;
        this.jsonMapper = jsonMapper;
    }

    /** Campos comuns aos dois eventos de vínculo (contratos v1). */
    record LinkEvent(UUID linkId, UUID clientId, UUID professionalId, String professionalType, Instant occurredAt) {}

    /** ClienteAtualizado v1. */
    record ClientUpdated(UUID clientId, String name, LocalDate birthDate, String gender, Instant occurredAt) {}

    /** ContaExcluida v1. */
    record AccountDeleted(UUID clientId, Instant occurredAt) {}

    @Bean
    Declarables clientEventsTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
        Binding activated = BindingBuilder.bind(queue).to(exchange).with(LINK_ACTIVATED);
        Binding ended = BindingBuilder.bind(queue).to(exchange).with(LINK_ENDED);
        Binding updated = BindingBuilder.bind(queue).to(exchange).with(CLIENT_UPDATED);
        Binding deleted = BindingBuilder.bind(queue).to(exchange).with(ACCOUNT_DELETED);
        return new Declarables(
                exchange, queue, QueueBuilder.durable(DEAD_LETTER_QUEUE).build(), activated, ended, updated, deleted);
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        Runnable handler = switch (routingKey) {
            case LINK_ACTIVATED -> {
                LinkEvent event = parseLink(message);
                yield () -> projectClientEvents.linkActivated(
                        event.linkId(),
                        event.clientId(),
                        event.professionalId(),
                        event.professionalType(),
                        event.occurredAt());
            }
            case LINK_ENDED -> {
                LinkEvent event = parseLink(message);
                yield () -> projectClientEvents.linkEnded(
                        event.linkId(),
                        event.clientId(),
                        event.professionalId(),
                        event.professionalType(),
                        event.occurredAt());
            }
            case CLIENT_UPDATED -> {
                ClientUpdated event = parseClient(message);
                yield () -> projectClientEvents.clientUpdated(new ClientDirectory.ProjectedClient(
                        event.clientId(), event.name(), event.birthDate(), event.gender(), event.occurredAt()));
            }
            case ACCOUNT_DELETED -> {
                AccountDeleted event = parseDeleted(message);
                yield () -> eraseDeletedClient.accountDeleted(event.clientId(), event.occurredAt());
            }
            default -> throw new AmqpRejectAndDontRequeueException("Routing key fora do que esta fila consome.");
        };
        processedEvents.processOnce(QUEUE, message, handler);
    }

    private LinkEvent parseLink(Message message) {
        LinkEvent event = read(message, LinkEvent.class);
        if (event.linkId() == null
                || event.clientId() == null
                || event.professionalId() == null
                || !("nutritionist".equals(event.professionalType())
                        || "personal_trainer".equals(event.professionalType()))
                || event.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("Evento de vínculo fora do contrato.");
        }
        return event;
    }

    private ClientUpdated parseClient(Message message) {
        ClientUpdated event = read(message, ClientUpdated.class);
        if (event.clientId() == null || event.name() == null || event.name().isBlank() || event.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("ClienteAtualizado fora do contrato.");
        }
        return event;
    }

    private AccountDeleted parseDeleted(Message message) {
        AccountDeleted event = read(message, AccountDeleted.class);
        if (event.clientId() == null || event.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("ContaExcluida fora do contrato.");
        }
        return event;
    }

    /** Payload que não é JSON do contrato vai para a fila de mensagens mortas; a mensagem não repete o payload. */
    private <T> T read(Message message, Class<T> type) {
        try {
            return jsonMapper.readValue(message.getBody(), type);
        } catch (JacksonException invalid) {
            throw new AmqpRejectAndDontRequeueException("Payload fora do contrato.");
        }
    }
}
