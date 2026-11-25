package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.nutrition.application.EraseDeletedClient;
import com.fithub.nutrition.domain.log.MealPhotoStorage;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Consumo do {@code ContaExcluida} no nutrition (DEV-0163, ADR-012): o que é do cliente sai, inclusive as fotos do
 * prato no bucket; os planos da nutricionista ficam sem o id dele; o dado de outro cliente fica intacto; a reentrega
 * não falha; a lista de contas excluídas tem expurgo de 30 dias.
 */
class AccountDeletionConsumerTest extends ServiceIntegrationTest {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EraseDeletedClient eraseDeletedClient;

    @Autowired
    RecordingStorage storage;

    @TestConfiguration
    static class Fakes {

        /** Este teste não chama a API: nenhum token é aceito. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("token inválido");
            };
        }

        @Bean
        @Primary
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }
    }

    /** Bucket de mentira: guarda as chaves gravadas e as apagadas. */
    static class RecordingStorage implements MealPhotoStorage {

        final Set<String> stored = ConcurrentHashMap.newKeySet();
        final Set<String> deleted = ConcurrentHashMap.newKeySet();

        @Override
        public void delete(String objectKey) {
            stored.remove(objectKey);
            deleted.add(objectKey);
        }

        @Override
        public List<StoredKey> list(String prefix) {
            return stored.stream()
                    .filter(key -> key.startsWith(prefix))
                    .map(key -> new StoredKey(key, Instant.now()))
                    .toList();
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
        public boolean copy(String fromKey, String toKey) {
            return false;
        }
    }

    /** Ids do que foi semeado para um cliente. */
    record Seeded(UUID client, UUID plan, String photoKey) {}

    private Seeded seed() {
        UUID client = UUID.randomUUID();
        UUID nutritionist = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)
                        values (:id, :c, :n, 'PUBLISHED', now(), now())
                        """)
                .param("id", plan)
                .param("c", client)
                .param("n", nutritionist)
                .update();
        jdbc.sql("""
                        insert into meal_plan_version (id, plan_id, number, name, starts_on, published_at, created_at)
                        values (:id, :plan, 1, 'Plano A', date '2026-11-01', now(), now())
                        """).param("id", version).param("plan", plan).update();
        UUID log = UUID.randomUUID();
        String photoKey = "meal-logs/%s/%s/photo-%s".formatted(client, log, UUID.randomUUID());
        jdbc.sql("""
                        insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position,
                                              logged_on, status, created_at, updated_at, deviation, photo_key,
                                              photo_uploaded_at)
                        values (:id, :c, :plan, :v, 1, 0, date '2026-11-20', 'PARTIAL', now(), now(), 'comi pizza',
                                :key, now())
                        """)
                .param("id", log)
                .param("c", client)
                .param("plan", plan)
                .param("v", version)
                .param("key", photoKey)
                .update();
        jdbc.sql("insert into meal_log_substitution (log_id, item_position, alternative) values (:log, 0, 0)")
                .param("log", log)
                .update();
        jdbc.sql("""
                        insert into food_restriction (id, client_id, kind, description, terms, recorded_by, created_at,
                                                      updated_at)
                        values (:id, :c, 'ALLERGY', 'amendoim', array['amendoim'], :n, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("n", nutritionist)
                .update();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :c, :n, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("n", nutritionist)
                .update();
        jdbc.sql("insert into client (client_id, name, updated_at) values (:c, 'Ana Paciente', now())")
                .param("c", client)
                .update();
        storage.stored.add(photoKey);
        return new Seeded(client, plan, photoKey);
    }

    private int count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    /** Linhas do cliente em cada tabela que a exclusão deve esvaziar. */
    private List<Integer> leftovers(UUID client) {
        return List.of(
                count(
                        "select count(*) from meal_log_substitution where log_id in"
                                + " (select id from meal_log where client_id = :id)",
                        client),
                count("select count(*) from meal_log where client_id = :id", client),
                count("select count(*) from food_restriction where client_id = :id", client),
                count("select count(*) from meal_plan where client_id = :id", client),
                count("select count(*) from client_link where client_id = :id", client),
                count("select count(*) from client where client_id = :id", client));
    }

    private void publish(String eventId, UUID client) {
        String payload = """
                {"clientId": "%s", "occurredAt": "2026-11-25T12:00:00Z"}
                """.formatted(client);
        assertThat(EventContracts.validate(EventContract.ACCOUNT_DELETED_V1, payload))
                .isEmpty();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(
                "fithub.client",
                "client.account-deleted.v1",
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private void awaitProcessed(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.sql("select count(*) from fithub_processed_event where event_id = cast(:id as uuid)")
                    .param("id", eventId)
                    .query(Integer.class)
                    .single();
            if (count > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("O evento não foi processado.");
    }

    private void awaitDeleted(String key) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (storage.deleted.contains(key)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("A foto do prato não saiu do bucket.");
    }

    @Test
    void apagaTudoDoClienteInclusiveAsFotosEOsPlanosFicamSemOIdDele() throws Exception {
        Seeded deleted = seed();
        Seeded other = seed();
        assertThat(leftovers(deleted.client())).containsExactly(1, 1, 1, 1, 1, 1);
        String eventId = UUID.randomUUID().toString();

        publish(eventId, deleted.client());
        awaitProcessed(eventId);
        awaitDeleted(deleted.photoKey());

        assertThat(leftovers(deleted.client())).containsOnly(0);
        assertThat(leftovers(other.client())).containsExactly(1, 1, 1, 1, 1, 1);
        assertThat(storage.stored).contains(other.photoKey());
        assertThat(storage.deleted).doesNotContain(other.photoKey());
        // O plano da nutricionista fica, com a versão, e o id do paciente trocado por outro que não é o de ninguém.
        UUID anonymous = jdbc.sql("select client_id from meal_plan where id = :id")
                .param("id", deleted.plan())
                .query(UUID.class)
                .single();
        assertThat(anonymous).isNotEqualTo(deleted.client()).isNotEqualTo(other.client());
        assertThat(count("select count(*) from meal_plan_version where plan_id = :id", deleted.plan()))
                .isEqualTo(1);
        assertThat(count("select count(*) from deleted_account where client_id = :id", deleted.client()))
                .isEqualTo(1);
    }

    @Test
    void reentregaEOutroEventoDaMesmaContaNaoFalham() throws Exception {
        Seeded deleted = seed();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        publish(first, deleted.client());
        publish(first, deleted.client());
        publish(second, deleted.client());
        awaitProcessed(second);

        assertThat(leftovers(deleted.client())).containsOnly(0);
        assertThat(count("select count(*) from deleted_account where client_id = :id", deleted.client()))
                .isEqualTo(1);
    }

    @Test
    void expurgoTiraDaListaSoQuemFoiExcluidoHaMaisDe30Dias() {
        Instant now = Instant.now();
        UUID old = insertDeleted(now.minus(Duration.ofDays(31)));
        UUID recent = insertDeleted(now.minus(Duration.ofDays(29)));

        eraseDeletedClient.purgeDeletedAccounts();

        assertThat(count("select count(*) from deleted_account where client_id = :id", old))
                .isZero();
        assertThat(count("select count(*) from deleted_account where client_id = :id", recent))
                .isEqualTo(1);
    }

    private UUID insertDeleted(Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", id)
                .param("at", Timestamp.from(at))
                .update();
        return id;
    }
}
