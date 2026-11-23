package com.fithub.client.infrastructure.messaging;

import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.commons.messaging.ProcessedEvents;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
 * Consumidor do {@code ProfissionalCadastrado} (DEV-0035): mantém a projeção de profissionais. Fila durável
 * própria ligada à exchange do account-service, consumo idempotente pela {@code java-commons} (reentrega não
 * duplica) e fila de mensagens mortas para o que falhar depois das tentativas.
 */
@Configuration(proxyBeanMethods = false)
class ProfessionalRegisteredListener {

    static final String EXCHANGE = "fithub.account";
    static final String ROUTING_KEY = "account.professional-registered.v1";
    static final String QUEUE = "client.professional-registered";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    private final RecordProfessional recordProfessional;
    private final ProcessedEvents processedEvents;
    private final JsonMapper jsonMapper;

    ProfessionalRegisteredListener(
            RecordProfessional recordProfessional, ProcessedEvents processedEvents, JsonMapper jsonMapper) {
        this.recordProfessional = recordProfessional;
        this.processedEvents = processedEvents;
        this.jsonMapper = jsonMapper;
    }

    /** Payload v1 ({@code libs/event-contracts}). */
    record Registered(UUID professionalId, String professionalType, String name, Instant occurredAt) {}

    @Bean
    TopicExchange accountExchange() {
        // Mesmas propriedades da declaração do account-service: durável, sem apagar sozinha.
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue professionalRegisteredQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue professionalRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding professionalRegisteredBinding() {
        return BindingBuilder.bind(professionalRegisteredQueue())
                .to(accountExchange())
                .with(ROUTING_KEY);
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message) {
        Registered event = parse(message);
        processedEvents.processOnce(
                QUEUE,
                message,
                () -> recordProfessional.registered(
                        event.professionalId(),
                        ProfessionalType.fromContract(event.professionalType()),
                        event.name(),
                        event.occurredAt()));
    }

    /** Payload que não é do contrato vai direto para a fila de mensagens mortas: tentar de novo não resolve. */
    private Registered parse(Message message) {
        try {
            Registered event = jsonMapper.readValue(message.getBody(), Registered.class);
            if (event.professionalId() == null
                    || event.professionalType() == null
                    || event.name() == null
                    || event.name().isBlank()
                    || event.occurredAt() == null) {
                throw new AmqpRejectAndDontRequeueException("ProfissionalCadastrado sem campo obrigatório.");
            }
            ProfessionalType.fromContract(event.professionalType());
            return event;
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("ProfissionalCadastrado fora do contrato.", invalid);
        }
    }
}
