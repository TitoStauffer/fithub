package com.fithub.eventcontracts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valida o payload de um evento contra o JSON Schema do contrato (DEV-0018). Feito para os testes de
 * produtor (o que o serviço grava no outbox) e de consumidor (o que o serviço sabe ler):
 *
 * <pre>{@code
 * // produtor: o payload que vai para OutboxWriter.append(routingKey, payload)
 * EventContracts.assertValid(EventContract.LINK_ACTIVATED_V1, payload);
 * // consumidor: o exemplo do contrato precisa ser lido pelo handler
 * String exemplo = EventContracts.example(EventContract.LINK_ACTIVATED_V1);
 * }</pre>
 *
 * <p>Os formatos ({@code uuid}, {@code date-time}, {@code date}) são verificados, não só anotados.
 */
public final class EventContracts {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    private static final Map<EventContract, JsonNode> SCHEMA_NODES = new EnumMap<>(EventContract.class);
    private static final Map<EventContract, Schema> SCHEMAS = new EnumMap<>(EventContract.class);

    static {
        for (EventContract contract : EventContract.values()) {
            JsonNode node = readSchema(contract);
            SCHEMA_NODES.put(contract, node);
            SCHEMAS.put(contract, REGISTRY.getSchema(node));
        }
    }

    private EventContracts() {
    }

    /** Violações do payload (JSON em texto) contra o contrato; lista vazia quando é válido. */
    public static List<String> validate(EventContract contract, String json) {
        JsonNode payload;
        try {
            payload = JSON.readTree(json);
        } catch (JacksonException e) {
            return List.of("payload não é JSON válido: " + e.getOriginalMessage());
        }
        return validate(contract, payload);
    }

    /** Violações do payload contra o contrato; lista vazia quando é válido. */
    public static List<String> validate(EventContract contract, JsonNode payload) {
        return SCHEMAS.get(contract).validate(payload).stream().map(Error::toString).toList();
    }

    /**
     * Valida pela routing key, como ela chega na mensagem. Routing key sem contrato é violação: evento
     * entre serviços sem schema não deveria existir.
     */
    public static List<String> validate(String routingKey, String json) {
        return EventContract.forRoutingKey(routingKey)
                .map(contract -> validate(contract, json))
                .orElseGet(() -> List.of("não existe contrato para a routing key '" + routingKey + "'"));
    }

    /**
     * Falha com {@link ContractViolationException} se o payload não cumpre o contrato. Aceita o objeto
     * que o serviço passa ao outbox (record, Map), um {@link JsonNode} ou o JSON em texto.
     */
    public static void assertValid(EventContract contract, Object payload) {
        List<String> violations = switch (payload) {
            case String json -> validate(contract, json);
            case JsonNode node -> validate(contract, node);
            default -> validate(contract, (JsonNode) JSON.valueToTree(payload));
        };
        if (!violations.isEmpty()) throw new ContractViolationException(contract, violations);
    }

    /** O primeiro exemplo do schema, em JSON: um payload válido para testes de consumidor. */
    public static String example(EventContract contract) {
        JsonNode examples = SCHEMA_NODES.get(contract).get("examples");
        if (examples == null || examples.isEmpty()) {
            throw new IllegalStateException("O contrato " + contract.routingKey() + " não tem exemplo.");
        }
        return JSON.writeValueAsString(examples.get(0));
    }

    /** O schema do contrato, como está no classpath. */
    public static JsonNode schema(EventContract contract) {
        return SCHEMA_NODES.get(contract).deepCopy();
    }

    private static JsonNode readSchema(EventContract contract) {
        try (InputStream input = EventContracts.class.getClassLoader().getResourceAsStream(contract.schemaResource())) {
            if (input == null) throw new IllegalStateException("Schema ausente no classpath: " + contract.schemaResource());
            return JSON.readTree(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
