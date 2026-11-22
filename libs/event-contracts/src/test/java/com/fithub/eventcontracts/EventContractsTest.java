package com.fithub.eventcontracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventContractsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // --- Catálogo ---

    @Test
    void everySchemaFileHasAContractAndEveryContractHasASchemaFile() throws IOException, URISyntaxException {
        Path directory = Path.of(EventContracts.class.getClassLoader().getResource(EventContract.SCHEMA_DIRECTORY).toURI());
        try (Stream<Path> files = Files.list(directory)) {
            List<String> schemaFiles = files.map(file -> file.getFileName().toString()).sorted().toList();
            List<String> contractFiles = Arrays.stream(EventContract.values())
                    .map(contract -> contract.routingKey() + ".schema.json").sorted().toList();
            assertThat(schemaFiles).isEqualTo(contractFiles);
        }
    }

    @ParameterizedTest
    @EnumSource(EventContract.class)
    void routingKeyIdAndTitleFollowTheNamingRule(EventContract contract) {
        assertThat(contract.routingKey()).matches("(account|client|training|nutrition)\\.[a-z]+(-[a-z]+)*\\.v[1-9][0-9]*");
        assertThat(contract.exchange()).isEqualTo("fithub." + contract.originService());
        JsonNode schema = EventContracts.schema(contract);
        assertThat(schema.get("$id").asString()).isEqualTo("urn:fithub:event:" + contract.routingKey());
        String version = contract.routingKey().substring(contract.routingKey().lastIndexOf(".v") + 2);
        assertThat(schema.get("title").asString()).isEqualTo(contract.engineeringName() + " v" + version);
        assertThat(EventContract.forRoutingKey(contract.routingKey())).contains(contract);
    }

    @Test
    void theSixInitialEventsOfTheEngineeringDocumentExist() {
        assertThat(Arrays.stream(EventContract.values()).map(EventContract::engineeringName)).containsExactlyInAnyOrder(
                "VinculoAtivado", "VinculoEncerrado", "ClienteAtualizado", "ProfissionalCadastrado", "AssinaturaAlterada",
                "ContaExcluida");
    }

    // --- Schemas ---

    @ParameterizedTest
    @EnumSource(EventContract.class)
    void schemaIsValidJsonSchema2020AndClosed(EventContract contract) {
        var metaSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        JsonNode schema = EventContracts.schema(contract);
        assertThat(metaSchema.validate(schema)).isEmpty();
        // Fechado: campo desconhecido no produtor é erro (o consumidor, em produção, ignora; README).
        assertThat(schema.get("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.get("required")).isNotEmpty();
        assertThat(schema.get("required").valueStream().map(JsonNode::asString)).contains("occurredAt");
    }

    @ParameterizedTest
    @EnumSource(EventContract.class)
    void everyExampleIsValid(EventContract contract) {
        for (JsonNode example : EventContracts.schema(contract).get("examples")) {
            assertThat(EventContracts.validate(contract, example)).as(example.toString()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(EventContract.class)
    void missingRequiredFieldIsAViolation(EventContract contract) {
        for (JsonNode field : EventContracts.schema(contract).get("required")) {
            ObjectNode payload = example(contract);
            payload.remove(field.asString());
            assertThat(EventContracts.validate(contract, payload)).as(field.asString()).isNotEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(EventContract.class)
    void unknownFieldIsAViolation(EventContract contract) {
        ObjectNode payload = example(contract);
        payload.put("campoNovoSemContrato", "x");
        assertThat(EventContracts.validate(contract, payload)).isNotEmpty();
    }

    @Test
    void formatsAreAsserted() {
        ObjectNode badUuid = example(EventContract.LINK_ACTIVATED_V1);
        badUuid.put("clientId", "cliente-123");
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, badUuid)).isNotEmpty();

        ObjectNode badInstant = example(EventContract.LINK_ENDED_V1);
        badInstant.put("occurredAt", "ontem às 10h");
        assertThat(EventContracts.validate(EventContract.LINK_ENDED_V1, badInstant)).isNotEmpty();

        ObjectNode badDate = example(EventContract.CLIENT_UPDATED_V1);
        badDate.put("birthDate", "17/05/1990");
        assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, badDate)).isNotEmpty();
    }

    @Test
    void enumsAndScopeAreClosed() {
        ObjectNode unknownType = example(EventContract.LINK_ACTIVATED_V1);
        unknownType.put("professionalType", "coach");
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, unknownType)).isNotEmpty();

        ObjectNode emptyScope = example(EventContract.LINK_ACTIVATED_V1);
        emptyScope.putArray("consentScope");
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, emptyScope)).isNotEmpty();

        ObjectNode repeatedScope = example(EventContract.LINK_ACTIVATED_V1);
        repeatedScope.putArray("consentScope").add("profile").add("profile");
        assertThat(EventContracts.validate(EventContract.LINK_ACTIVATED_V1, repeatedScope)).isNotEmpty();
    }

    @Test
    void optionalProfileFieldsAcceptExplicitNull() {
        ObjectNode payload = example(EventContract.CLIENT_UPDATED_V1);
        payload.putNull("birthDate");
        payload.putNull("gender");
        assertThat(EventContracts.validate(EventContract.CLIENT_UPDATED_V1, payload)).isEmpty();
    }

    // --- Validação pela routing key e entradas inválidas ---

    @Test
    void routingKeyWithoutContractAndInvalidJsonAreViolations() {
        assertThat(EventContracts.validate("client.algo-sem-contrato.v1", "{}"))
                .containsExactly("não existe contrato para a routing key 'client.algo-sem-contrato.v1'");
        assertThat(EventContracts.validate(EventContract.ACCOUNT_DELETED_V1.routingKey(), "{ não é json"))
                .singleElement().asString().startsWith("payload não é JSON válido");
        assertThat(EventContracts.validate(EventContract.ACCOUNT_DELETED_V1.routingKey(), EventContracts.example(EventContract.ACCOUNT_DELETED_V1)))
                .isEmpty();
    }

    // --- Uso em teste de produtor e de consumidor ---

    /** Como um produtor modelaria o evento no próprio serviço (o tipo é dele, não da lib). */
    record LinkActivated(UUID linkId, UUID clientId, UUID professionalId, String professionalType,
            List<String> consentScope, Instant occurredAt) {
    }

    @Test
    void producerPayloadIsCheckedBeforeGoingToTheOutbox() {
        var payload = new LinkActivated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "nutritionist",
                List.of("profile", "body_assessments"), Instant.parse("2026-11-22T12:00:00Z"));
        EventContracts.assertValid(EventContract.LINK_ACTIVATED_V1, payload);

        var withoutScope = Map.of("linkId", UUID.randomUUID().toString(), "clientId", UUID.randomUUID().toString(),
                "professionalId", UUID.randomUUID().toString(), "professionalType", "nutritionist",
                "occurredAt", "2026-11-22T12:00:00Z");
        assertThatThrownBy(() -> EventContracts.assertValid(EventContract.LINK_ACTIVATED_V1, withoutScope))
                .isInstanceOfSatisfying(ContractViolationException.class, violation -> {
                    assertThat(violation.contract()).isEqualTo(EventContract.LINK_ACTIVATED_V1);
                    assertThat(violation.violations()).singleElement().asString().contains("consentScope");
                    assertThat(violation).hasMessageStartingWith("Payload fora do contrato client.link-activated.v1");
                });
    }

    /** Como um consumidor leria só o que precisa, ignorando campos que não usa. */
    record LinkEndedView(UUID clientId, UUID professionalId, String professionalType) {
    }

    @Test
    void consumerReadsTheContractExample() {
        JsonMapper consumerMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        LinkEndedView view = consumerMapper.readValue(EventContracts.example(EventContract.LINK_ENDED_V1), LinkEndedView.class);
        assertThat(view.clientId()).isNotNull();
        assertThat(view.professionalType()).isEqualTo("personal_trainer");
    }

    private static ObjectNode example(EventContract contract) {
        return (ObjectNode) JSON.readTree(EventContracts.example(contract));
    }
}
