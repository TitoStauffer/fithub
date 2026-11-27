package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.DeleteClientAccount;
import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.assessment.PhotoStorage;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.client.domain.profile.ClientEvents;
import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exclusão da conta do cliente (DEV-0105, ADR-012): o que sai, o que fica, a transação com o {@code ContaExcluida},
 * os arquivos do bucket depois do commit e o expurgo da lista de contas excluídas.
 */
@Import({ClientApiFakes.class, AccountDeletionTest.Fakes.class})
class AccountDeletionTest extends ServiceIntegrationTest {

    private static final String DELETED = "client.account-deleted.v1";

    private static final String PROFILE = """
            {"name": "Ana", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165, "goal": "HEALTH"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    @Autowired
    DeleteClientAccount deleteClientAccount;

    @Autowired
    FailingEvents events;

    @Autowired
    RecordingStorage storage;

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        FailingEvents failingEvents(@Qualifier("outboxClientEvents") ClientEvents outbox) {
            return new FailingEvents(outbox);
        }

        @Bean
        @Primary
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }
    }

    /** Grava pelo outbox de verdade e, se pedido, falha logo depois do ContaExcluida, dentro da transação. */
    static class FailingEvents implements ClientEvents {

        final AtomicBoolean failAfterDeleted = new AtomicBoolean();
        private final ClientEvents outbox;

        FailingEvents(ClientEvents outbox) {
            this.outbox = outbox;
        }

        @Override
        public void updated(ClientProfile profile) {
            outbox.updated(profile);
        }

        @Override
        public void deleted(UUID clientId, Instant at) {
            outbox.deleted(clientId, at);
            if (failAfterDeleted.get()) {
                throw new IllegalStateException("falha depois de gravar o evento");
            }
        }
    }

    /** Bucket de mentira: registra os prefixos apagados. */
    static class RecordingStorage implements PhotoStorage {

        final List<String> deletedPrefixes = new CopyOnWriteArrayList<>();

        @Override
        public void deleteAll(String prefix) {
            deletedPrefixes.add(prefix);
        }

        @Override
        public URI presignUpload(String objectKey, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI presignDownload(String objectKey) {
            return URI.create("http://bucket.test/" + objectKey);
        }

        @Override
        public Optional<StoredObject> describe(String objectKey) {
            return Optional.empty();
        }

        @Override
        public void delete(String objectKey) {}

        @Override
        public boolean copy(String fromKey, String toKey) {
            return false;
        }

        @Override
        public List<StoredKey> list(String prefix) {
            return List.of();
        }
    }

    @AfterEach
    void reset() {
        events.failAfterDeleted.set(false);
        storage.deletedPrefixes.clear();
    }

    /** Cliente com perfil, saúde, avaliação com foto, vínculo aceito com consentimento e convite pendente. */
    private String fullClient(String professional) throws Exception {
        String client = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE))
                .andExpect(status().isCreated());
        mvc.perform(
                        put("/clients/me/health")
                                .header("Authorization", "Bearer conta-" + client)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"consent\": true, \"termsVersion\": \"v1\", \"conditions\": [\"HYPERTENSION\"], \"notes\": \"anotação\"}"))
                .andExpect(status().isOk());
        String assessment = json.readTree(mvc.perform(post("/clients/me/assessments")
                                .header("Authorization", "Bearer conta-" + client)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"measuredOn\": \"2026-11-20\", \"weightKg\": 64.0}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
        jdbc.sql("""
                        insert into assessment_photo (assessment_id, client_id, pose, object_key, uploaded_at)
                        values (:assessment, :client, 'FRONT', :key, now())
                        """)
                .param("assessment", UUID.fromString(assessment))
                .param("client", UUID.fromString(client))
                .param(
                        "key",
                        "clients/%s/assessments/%s/front-photo-%s".formatted(client, assessment, UUID.randomUUID()))
                .update();
        String link = json.readTree(mvc.perform(post("/invitations")
                                .header("Authorization", "Bearer personal-" + professional)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("link")
                .asString();
        mvc.perform(post("/links")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"consent\": true, \"termsVersion\": \"v2\"}"
                                .formatted(link.substring(link.lastIndexOf('/') + 1))))
                .andExpect(status().isCreated());
        // O convite aceito foi enviado para outro e-mail do cliente: sai pelo vínculo, não pelo e-mail do token.
        jdbc.sql(
                        "update invitation set email = :email where id = (select invitation_id from client_link where client_id = :c)")
                .param("email", "aceito-" + client + "@teste.fithub")
                .param("c", UUID.fromString(client))
                .update();
        invitation(professional, client.toUpperCase() + "@TESTE.FITHUB", "PENDING");
        invitation(professional, client + "@teste.fithub", "CANCELLED");
        jdbc.sql(
                        "insert into health_access_log (id, professional_id, client_id, accessed_at) values (:id, :p, :c, now())")
                .param("id", UUID.randomUUID())
                .param("p", UUID.fromString(professional))
                .param("c", UUID.fromString(client))
                .update();
        return client;
    }

    private void invitation(String professional, String email, String invitationStatus) {
        jdbc.sql("""
                        insert into invitation (id, professional_id, email, token_hash, code_hash, status, expires_at,
                                                created_at, cancelled_at)
                        values (:id, :p, :email, :token, :code, :status, now() + interval '7 days', now(),
                                case when :status = 'CANCELLED' then now() end)
                        """)
                .param("id", UUID.randomUUID())
                .param("p", UUID.fromString(professional))
                .param("email", email)
                .param("status", invitationStatus)
                .param("token", hash())
                .param("code", hash())
                .update();
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private String professional() {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(
                UUID.fromString(account),
                ProfessionalType.PERSONAL_TRAINER,
                "Paulo Personal",
                Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private ResultActions deleteAccount(String token) throws Exception {
        return mvc.perform(delete("/clients/me").header("Authorization", "Bearer " + token));
    }

    private int count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    /** Quantas linhas do cliente ficaram em cada tabela que a exclusão deve esvaziar. */
    private List<Integer> leftovers(String client) {
        UUID id = UUID.fromString(client);
        return List.of(
                count("select count(*) from client_profile where account_id = :id", client),
                count("select count(*) from client_health where account_id = :id", client),
                count("select count(*) from health_consent where account_id = :id", client),
                count("select count(*) from body_assessment where client_id = :id", id),
                count("select count(*) from assessment_photo where client_id = :id", id),
                count("select count(*) from client_link where client_id = :id", id),
                count("select count(*) from consent where client_id = :id", id),
                count("select count(*) from invitation where position(lower(:id) in lower(email)) > 0", client));
    }

    private List<String> outboxPayloads(String client) {
        return jdbc.sql("""
                        select payload::text from fithub_outbox_event
                         where routing_key = :routingKey and payload ->> 'clientId' = :client
                        """)
                .param("routingKey", DELETED)
                .param("client", client)
                .query(String.class)
                .list();
    }

    /** VinculoEncerrado gravados no outbox para o cliente. */
    private List<String> linkEndedPayloads(String client) {
        return jdbc.sql("""
                        select payload::text from fithub_outbox_event
                         where routing_key = 'client.link-ended.v1' and payload ->> 'clientId' = :client
                        """).param("client", client).query(String.class).list();
    }

    private int deletedAccounts(String client) {
        return count("select count(*) from deleted_account where client_id = :id", UUID.fromString(client));
    }

    @Test
    void excluirApagaTudoDoClienteGravaOEventoEApagaOsArquivosDoBucket() throws Exception {
        String client = fullClient(professional());
        assertThat(leftovers(client)).containsExactly(1, 1, 1, 1, 1, 1, 1, 3);

        deleteAccount("conta-" + client).andExpect(status().isNoContent());

        assertThat(leftovers(client)).containsOnly(0);
        assertThat(deletedAccounts(client)).isEqualTo(1);
        List<String> payloads = outboxPayloads(client);
        assertThat(payloads).hasSize(1);
        assertThat(EventContracts.validate(EventContract.ACCOUNT_DELETED_V1, payloads.getFirst()))
                .isEmpty();
        assertThat(payloads.getFirst()).contains("\"occurredAt\": \"2026-11-23T12:00:00Z\"");
        // Só o prefixo deste cliente importa: a reaplicação da DEV-0172, na subida, pode apagar o de outros da lista.
        assertThat(storage.deletedPrefixes).contains("clients/" + client + "/");
    }

    @Test
    void osVinculosAtivosSaoEncerradosPeloClienteComOVinculoEncerradoAntesDeSumir() throws Exception {
        String professional = professional();
        String client = fullClient(professional);
        String linkId = jdbc.sql("select id::text from client_link where client_id = :c")
                .param("c", UUID.fromString(client))
                .query(String.class)
                .single();

        deleteAccount("conta-" + client).andExpect(status().isNoContent());

        List<String> ended = linkEndedPayloads(client);
        assertThat(ended).hasSize(1);
        assertThat(EventContracts.validate(EventContract.LINK_ENDED_V1, ended.getFirst()))
                .isEmpty();
        assertThat(ended.getFirst())
                .contains("\"linkId\": \"" + linkId + "\"")
                .contains("\"professionalId\": \"" + professional + "\"")
                .contains("\"endedBy\": \"client\"")
                .contains("\"occurredAt\": \"2026-11-23T12:00:00Z\"");
    }

    @Test
    void outrosClientesOLadoProfissionalDaContaEORegistroDeLeituraDaSaudeFicam() throws Exception {
        String professional = professional();
        String client = fullClient(professional);
        String other = fullClient(professional);
        // A conta que se exclui também é profissional, com um cliente dela.
        recordProfessional.registered(
                UUID.fromString(client),
                ProfessionalType.PERSONAL_TRAINER,
                "Ana Personal",
                Instant.parse("2026-11-21T10:00:00Z"));
        String ownClient = UUID.randomUUID().toString();
        jdbc.sql("""
                        insert into client_link (id, client_id, professional_id, professional_type, status, started_at)
                        values (:id, :c, :p, 'PERSONAL_TRAINER', 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", UUID.fromString(ownClient))
                .param("p", UUID.fromString(client))
                .update();

        deleteAccount("personal-" + client).andExpect(status().isNoContent());

        assertThat(leftovers(other)).containsExactly(1, 1, 1, 1, 1, 1, 1, 3);
        assertThat(count("select count(*) from professional where id = :id", UUID.fromString(client)))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from client_link where professional_id = :id and status = 'ACTIVE'",
                        UUID.fromString(client)))
                .isEqualTo(1);
        assertThat(linkEndedPayloads(ownClient)).isEmpty();
        assertThat(linkEndedPayloads(other)).isEmpty();
        assertThat(count("select count(*) from health_access_log where client_id = :id", UUID.fromString(client)))
                .isEqualTo(1);
        assertThat(count("select count(*) from invitation where professional_id = :id", UUID.fromString(professional)))
                .isEqualTo(6);
        assertThat(deletedAccounts(other)).isZero();
    }

    @Test
    void falhaNoMeioDaTransacaoNaoApagaNadaNemPublicaNemMexeNoBucket() throws Exception {
        String client = fullClient(professional());
        events.failAfterDeleted.set(true);

        deleteAccount("conta-" + client).andExpect(status().isInternalServerError());

        assertThat(leftovers(client)).containsExactly(1, 1, 1, 1, 1, 1, 1, 3);
        assertThat(outboxPayloads(client)).isEmpty();
        assertThat(linkEndedPayloads(client)).isEmpty();
        assertThat(count(
                        "select count(*) from client_link where client_id = :id and status = 'ACTIVE'",
                        UUID.fromString(client)))
                .isEqualTo(1);
        assertThat(deletedAccounts(client)).isZero();
        assertThat(storage.deletedPrefixes).doesNotContain("clients/" + client + "/");
    }

    @Test
    void repetirAExclusaoOuExcluirSemNadaNoServicoResponde204() throws Exception {
        String client = fullClient(professional());
        String empty = UUID.randomUUID().toString();

        deleteAccount("conta-" + client).andExpect(status().isNoContent());
        deleteAccount("conta-" + client).andExpect(status().isNoContent());
        deleteAccount("conta-" + empty).andExpect(status().isNoContent());

        assertThat(deletedAccounts(client)).isEqualTo(1);
        assertThat(outboxPayloads(empty)).hasSize(1);
    }

    @Test
    void semTokenOuSemOPapelDeClienteNaoExclui() throws Exception {
        String client = fullClient(professional());

        mvc.perform(delete("/clients/me")).andExpect(status().isUnauthorized());
        deleteAccount("sempapel-" + client).andExpect(status().isForbidden());

        assertThat(leftovers(client)).containsExactly(1, 1, 1, 1, 1, 1, 1, 3);
        assertThat(outboxPayloads(client)).isEmpty();
    }

    @Test
    void expurgoTiraDaListaSoQuemFoiExcluidoHaMaisDe30Dias() {
        // No teste, agora é 23/11/2026 12:00 UTC.
        UUID old = insertDeleted("2026-10-24T11:59:59Z");
        UUID exactly = insertDeleted("2026-10-24T12:00:00Z");
        UUID recent = insertDeleted("2026-11-20T12:00:00Z");

        int removed = deleteClientAccount.purgeDeletedAccounts();

        assertThat(removed).isEqualTo(1);
        assertThat(count("select count(*) from deleted_account where client_id = :id", old))
                .isZero();
        assertThat(count("select count(*) from deleted_account where client_id = :id", exactly))
                .isEqualTo(1);
        assertThat(count("select count(*) from deleted_account where client_id = :id", recent))
                .isEqualTo(1);
    }

    private UUID insertDeleted(String at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", id)
                .param("at", Timestamp.from(Instant.parse(at)))
                .update();
        return id;
    }
}
