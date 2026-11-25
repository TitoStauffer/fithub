package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.application.MealLogPhotos;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.log.MealLog;
import com.fithub.nutrition.domain.log.MealLogRepository;
import com.fithub.nutrition.domain.log.MealPhotoStorage;
import com.fithub.nutrition.domain.plan.MealPlanRepository;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Desvio e foto do prato (DEV-0088) contra o SeaweedFS real, na mesma versão do compose e do client-service. */
class MealLogPhotoApiTest extends ServiceIntegrationTest {

    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:4.54")
            .withCommand("server -s3 -dir=/data -master.telemetry=false")
            .withEnv("AWS_ACCESS_KEY_ID", "fithub-test")
            .withEnv("AWS_SECRET_ACCESS_KEY", "fithub-test-secret")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/healthz").forPort(8333));

    static {
        SEAWEEDFS.start();
    }

    private static String endpoint() {
        return "http://%s:%d".formatted(SEAWEEDFS.getHost(), SEAWEEDFS.getMappedPort(8333));
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("fithub.storage.endpoint", MealLogPhotoApiTest::endpoint);
        registry.add("fithub.storage.public-endpoint", MealLogPhotoApiTest::endpoint);
        registry.add("fithub.storage.access-key", () -> "fithub-test");
        registry.add("fithub.storage.secret-key", () -> "fithub-test-secret");
    }

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22, (byte) 0xFF, (byte) 0xD9
    };

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    FoodRepository foods;

    @Autowired
    MealLogRepository logs;

    @Autowired
    MealPlanRepository plans;

    @Autowired
    MealPhotoStorage storage;

    @Autowired
    MealLogPhotos photos;

    private UUID nutritionist;
    private UUID client;
    private String planId;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>}: nutricionista; {@code conta-<sub>}: cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("nutri-", List.of("client", "nutritionist"), "conta-", List.of("client"));
            return token -> rolesByPrefix.entrySet().stream()
                    .filter(entry -> token.startsWith(entry.getKey()))
                    .findFirst()
                    .map(entry -> Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject(token.substring(entry.getKey().length()))
                            .claim("realm_access", Map.of("roles", entry.getValue()))
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(300))
                            .build())
                    .orElseThrow(() -> new BadJwtException("token inválido"));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        nutritionist = UUID.randomUUID();
        client = UUID.randomUUID();
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :client, :professional, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("client", client)
                .param("professional", nutritionist)
                .update();
        Food rice = new Food(
                UUID.randomUUID(),
                Food.Kind.TACO,
                null,
                "Arroz, tipo 1, cozido",
                Food.BaseUnit.G,
                new Food.Nutrients(
                        new BigDecimal("128"),
                        new BigDecimal("2.5"),
                        new BigDecimal("28"),
                        new BigDecimal("0.2"),
                        null),
                true,
                new Food.Source("TACO 4ª edição", UUID.randomUUID().toString()),
                Instant.now(),
                Instant.now());
        foods.save(rice);
        String location = mvc.perform(post("/clients/" + client + "/meal-plans")
                        .header("Authorization", "Bearer nutri-" + nutritionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\": \"Plano\", \"startsOn\": \"2026-12-01\", \"meals\": [{\"name\": \"Almoço\","
                                        + " \"items\": [{\"foodId\": \"" + rice.id() + "\", \"amount\": 100}]}]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        planId = location.substring(location.lastIndexOf('/') + 1);
        mvc.perform(post("/meal-plans/" + planId + "/publish").header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk());
    }

    private String patient() {
        return "Bearer conta-" + client;
    }

    private UUID newLog(String deviation) throws Exception {
        UUID logId = UUID.randomUUID();
        logMeal(patient(), logId, deviation).andExpect(status().isOk());
        return logId;
    }

    private ResultActions logMeal(String token, UUID logId, String deviation) throws Exception {
        String body = "{\"planId\": \"" + planId + "\", \"mealPosition\": 0, \"status\": \"SKIPPED\""
                + (deviation == null ? "" : ", \"deviation\": " + json.writeValueAsString(deviation)) + "}";
        return mvc.perform(put("/me/meal-logs/" + logId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions requestUpload(String token, UUID logId, String contentType) throws Exception {
        return mvc.perform(post("/me/meal-logs/" + logId + "/photo/upload-url")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\": \"" + contentType + "\"}"));
    }

    private ResultActions confirm(String token, UUID logId, String uploadId) throws Exception {
        return mvc.perform(put("/me/meal-logs/" + logId + "/photo")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\": \"" + uploadId + "\"}"));
    }

    /** Pede a URL, envia o JPEG por ela, como o app faz, e devolve o id do envio. */
    private String upload(UUID logId) throws Exception {
        JsonNode reply = json.readTree(requestUpload(patient(), logId, "image/jpeg")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        HttpResponse<Void> sent = http.send(
                HttpRequest.newBuilder(URI.create(reply.get("uploadUrl").asString()))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(JPEG))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(sent.statusCode()).isEqualTo(200);
        return reply.get("uploadId").asString();
    }

    private String prefix(UUID logId) {
        return "meal-logs/" + client + "/" + logId + "/";
    }

    @Test
    void desvioVaiNoRegistroAte500Caracteres() throws Exception {
        UUID logId = UUID.randomUUID();
        logMeal(patient(), logId, "  Comi pizza no lugar  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviation").value("Comi pizza no lugar"));
        logMeal(patient(), logId, "Comi metade")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviation").value("Comi metade"));
        logMeal(patient(), logId, "a".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deviation"));
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.logs[0].deviation").value("Comi metade"));
    }

    @Test
    void enviaConfirmaLeESubstituiAFotoDoPrato() throws Exception {
        UUID logId = newLog("Troquei o arroz");
        String first = upload(logId);
        String url = json.readTree(confirm(patient(), logId, first)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.photoUrlExpiresAt").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("photoUrl")
                .asString();
        HttpResponse<byte[]> read =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(JPEG);
        mvc.perform(get("/me/meal-plan/today").header("Authorization", patient()))
                .andExpect(jsonPath("$.logs[0].photoUrl").exists());

        // A segunda foto substitui a primeira: no bucket fica só ela.
        String second = upload(logId);
        confirm(patient(), logId, second).andExpect(status().isOk());
        assertThat(storage.list(prefix(logId)))
                .extracting(MealPhotoStorage.StoredKey::objectKey)
                .containsExactly(prefix(logId) + "photo-" + second);
        // A URL de envio usada de novo não alcança a foto guardada.
        assertThat(logs.findById(logId).orElseThrow().photo().objectKey()).isEqualTo(prefix(logId) + "photo-" + second);
    }

    @Test
    void tipoEnvioOuArquivoInvalidosRespondem400() throws Exception {
        UUID logId = newLog(null);
        requestUpload(patient(), logId, "image/gif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentType"));
        confirm(patient(), logId, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("uploadId"));
        confirm(patient(), logId, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("uploadId"));

        // Um arquivo que não é imagem, gravado direto no bucket na chave do envio: a confirmação recusa e apaga.
        String uploadId = UUID.randomUUID().toString();
        String bigUploadId = UUID.randomUUID().toString();
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("fithub-test", "fithub-test-secret")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.create())
                .build()) {
            upload(logId); // cria o bucket
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket("fithub-meal-photos")
                            .key(prefix(logId) + "uploads/" + uploadId)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromString("não é foto"));
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket("fithub-meal-photos")
                            .key(prefix(logId) + "uploads/" + bigUploadId)
                            .contentType("image/jpeg")
                            .build(),
                    RequestBody.fromBytes(new byte[(int) MealLog.MAX_PHOTO_BYTES + 1]));
        }
        confirm(patient(), logId, uploadId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photo"));
        assertThat(storage.describe(prefix(logId) + "photo-" + uploadId)).isEmpty();
        // O arquivo do envio recusado também sai: não fica esperando o expurgo.
        assertThat(storage.describe(prefix(logId) + "uploads/" + uploadId)).isEmpty();
        // JPEG acima de 10 MB também é recusado e apagado.
        confirm(patient(), logId, bigUploadId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photo"));
        assertThat(storage.describe(prefix(logId) + "photo-" + bigUploadId)).isEmpty();
        assertThat(logs.findById(logId).orElseThrow().photo()).isNull();
    }

    @Test
    void depoisDoDiaAFotoNaoMudaEOutroPacienteNaoAlcanca() throws Exception {
        UUID logId = newLog(null);
        String uploadId = upload(logId);
        String stranger = "Bearer conta-" + UUID.randomUUID();
        requestUpload(stranger, logId, "image/jpeg").andExpect(status().isNotFound());
        confirm(stranger, logId, uploadId).andExpect(status().isNotFound());
        mvc.perform(delete("/me/meal-logs/" + logId + "/photo").header("Authorization", stranger))
                .andExpect(status().isNotFound());

        jdbc.sql("update meal_log set logged_on = logged_on - 1 where id = :id")
                .param("id", logId)
                .update();
        requestUpload(patient(), logId, "image/jpeg").andExpect(status().isConflict());
        confirm(patient(), logId, uploadId).andExpect(status().isConflict());
        mvc.perform(delete("/me/meal-logs/" + logId + "/photo").header("Authorization", patient()))
                .andExpect(status().isConflict());
    }

    @Test
    void tirarAFotoOuApagarORegistroLevaOsArquivos() throws Exception {
        UUID logId = newLog(null);
        confirm(patient(), logId, upload(logId)).andExpect(status().isOk());
        mvc.perform(delete("/me/meal-logs/" + logId + "/photo").header("Authorization", patient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
        assertThat(storage.list(prefix(logId))).isEmpty();

        confirm(patient(), logId, upload(logId)).andExpect(status().isOk());
        mvc.perform(delete("/me/meal-logs/" + logId).header("Authorization", patient()))
                .andExpect(status().isNoContent());
        assertThat(storage.list(prefix(logId))).isEmpty();
    }

    @Test
    void correcaoQueChegaJuntoComAFotoNaoATira() throws Exception {
        UUID logId = newLog(null);
        MealLog stale = logs.findById(logId).orElseThrow();
        confirm(patient(), logId, upload(logId)).andExpect(status().isOk());

        // A correção lida antes da foto grava só as colunas dela: a foto continua.
        MealLog corrected = stale.correct(
                plans.findVersion(stale.planId(), stale.versionNumber()).orElseThrow(),
                0,
                MealLog.Status.DONE,
                List.of(),
                "Comi tudo",
                LocalDate.now(ZoneId.of("America/Sao_Paulo")),
                Instant.now());
        logs.update(corrected);
        assertThat(logs.findById(logId).orElseThrow().photo()).isNotNull();
        assertThat(logs.findById(logId).orElseThrow().deviation()).isEqualTo("Comi tudo");
    }

    @Test
    void expurgoLevaSoOEnvioQueNuncaFoiConfirmado() throws Exception {
        UUID logId = newLog(null);
        String confirmed = upload(logId);
        confirm(patient(), logId, confirmed).andExpect(status().isOk());
        String pending = upload(logId);

        assertThat(photos.purgeUnconfirmed(Instant.now().minusSeconds(3600))).isZero();
        assertThat(photos.purgeUnconfirmed(Instant.now().plusSeconds(60))).isGreaterThanOrEqualTo(1);
        assertThat(storage.list(prefix(logId)))
                .extracting(MealPhotoStorage.StoredKey::objectKey)
                .containsExactly(prefix(logId) + "photo-" + confirmed)
                .doesNotContain(prefix(logId) + "uploads/" + pending);
    }
}
