package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.ManagePhotos;
import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.assessment.PhotoStorage;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Fotos de evolução (DEV-0048) contra o SeaweedFS real, na mesma versão do compose. */
@Import(ClientApiFakes.class)
@ExtendWith(OutputCaptureExtension.class)
class PhotoApiTest extends ServiceIntegrationTest {

    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:4.54")
            .withCommand("server -s3 -dir=/data -master.telemetry=false")
            .withEnv("AWS_ACCESS_KEY_ID", "fithub-test")
            .withEnv("AWS_SECRET_ACCESS_KEY", "fithub-test-secret")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/healthz").forPort(8333));

    static {
        SEAWEEDFS.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        String endpoint = "http://%s:%d".formatted(SEAWEEDFS.getHost(), SEAWEEDFS.getMappedPort(8333));
        registry.add("fithub.storage.endpoint", () -> endpoint);
        registry.add("fithub.storage.public-endpoint", () -> endpoint);
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
    RecordProfessional recordProfessional;

    @Autowired
    PhotoStorage storage;

    @Autowired
    ManagePhotos managePhotos;

    private String client() throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """))
                .andExpect(status().isCreated());
        return account;
    }

    private String assessment(String client) throws Exception {
        return read(mvc.perform(post("/clients/me/assessments")
                                .header("Authorization", "Bearer conta-" + client)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"measuredOn\": \"2026-11-20\", \"weightKg\": 64.0}"))
                        .andExpect(status().isCreated()))
                .get("id")
                .asString();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions uploadUrl(String client, String assessment, String pose, String contentType)
            throws Exception {
        return mvc.perform(post("/clients/me/assessments/%s/photos/%s/upload-url".formatted(assessment, pose))
                .header("Authorization", "Bearer conta-" + client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\": \"%s\"}".formatted(contentType)));
    }

    private ResultActions confirm(String client, String assessment, String pose, String uploadId) throws Exception {
        return mvc.perform(post("/clients/me/assessments/%s/photos/%s/confirm".formatted(assessment, pose))
                .header("Authorization", "Bearer conta-" + client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\": \"%s\"}".formatted(uploadId)));
    }

    private int putFile(String url, String contentType, byte[] bytes) throws Exception {
        return http.send(
                        HttpRequest.newBuilder(URI.create(url))
                                .header("Content-Type", contentType)
                                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private HttpResponse<byte[]> getFile(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Envia e confirma a foto da pose; devolve a resposta da confirmação. */
    private JsonNode sendPhoto(String client, String assessment, String pose, byte[] bytes) throws Exception {
        JsonNode upload = read(uploadUrl(client, assessment, pose, "image/jpeg").andExpect(status().isOk()));
        assertThat(putFile(upload.get("uploadUrl").asString(), "image/jpeg", bytes))
                .isEqualTo(200);
        return read(confirm(client, assessment, pose, upload.get("uploadId").asString())
                .andExpect(status().isOk()));
    }

    @Test
    void clienteEnviaConfirmaELeAFotoPorUrlTemporaria() throws Exception {
        String client = client();
        String assessment = assessment(client);

        JsonNode photo = sendPhoto(client, assessment, "FRONT", JPEG);

        assertThat(photo.get("pose").asString()).isEqualTo("FRONT");
        assertThat(photo.get("url").asString()).contains("X-Amz-Signature").contains("X-Amz-Expires=300");
        assertThat(getFile(photo.get("url").asString()).body()).isEqualTo(JPEG);
        mvc.perform(get("/clients/me/assessments/%s/photos".formatted(assessment))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pose").value("FRONT"));
    }

    @Test
    void oBucketNaoEPublicoSemAssinaturaNaoSeLe() throws Exception {
        String client = client();
        JsonNode photo = sendPhoto(client, assessment(client), "SIDE", JPEG);
        String unsigned = photo.get("url").asString().replaceAll("\\?.*$", "");

        assertThat(getFile(unsigned).statusCode()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void enviarDeNovoSubstituiAFotoEApagaAAnterior() throws Exception {
        String client = client();
        String assessment = assessment(client);
        JsonNode first = sendPhoto(client, assessment, "BACK", JPEG);
        String firstKey = jdbc.sql("select object_key from assessment_photo where assessment_id = :id")
                .param("id", UUID.fromString(assessment))
                .query(String.class)
                .single();

        byte[] second = Arrays.copyOf(JPEG, JPEG.length + 1);
        sendPhoto(client, assessment, "BACK", second);

        assertThat(getFile(first.get("url").asString()).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.sql("select object_key from assessment_photo where assessment_id = :id")
                        .param("id", UUID.fromString(assessment))
                        .query(String.class)
                        .single())
                .isNotEqualTo(firstKey);
    }

    @Test
    void tipoForaDeJpegOuPngEArquivoAcimaDe10MbSaoRecusados() throws Exception {
        String client = client();
        String assessment = assessment(client);

        uploadUrl(client, assessment, "FRONT", "image/gif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentType"));

        JsonNode upload =
                read(uploadUrl(client, assessment, "FRONT", "image/jpeg").andExpect(status().isOk()));
        assertThat(putFile(upload.get("uploadUrl").asString(), "image/jpeg", new byte[10 * 1024 * 1024 + 1]))
                .isEqualTo(200);
        confirm(client, assessment, "FRONT", upload.get("uploadId").asString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photo"));
        // O arquivo grande foi apagado: confirmar de novo não acha o envio.
        confirm(client, assessment, "FRONT", upload.get("uploadId").asString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("uploadId"));
    }

    @Test
    void confirmarSemEnviarERecusado() throws Exception {
        String client = client();
        String assessment = assessment(client);
        JsonNode upload =
                read(uploadUrl(client, assessment, "FRONT", "image/png").andExpect(status().isOk()));

        confirm(client, assessment, "FRONT", upload.get("uploadId").asString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("uploadId"));
        confirm(client, assessment, "FRONT", "nao-e-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void profissionalVinculadoVeEOutrosNao() throws Exception {
        String client = client();
        String assessment = assessment(client);
        sendPhoto(client, assessment, "FRONT", JPEG);
        String personal = UUID.randomUUID().toString();
        recordProfessional.registered(
                UUID.fromString(personal),
                ProfessionalType.PERSONAL_TRAINER,
                "Paula",
                Instant.parse("2026-11-20T10:00:00Z"));
        String code = jdbc.sql("select invite_code from professional where id = :id")
                .param("id", UUID.fromString(personal))
                .query(String.class)
                .single();
        String path = "/professionals/me/clients/%s/assessments/%s/photos".formatted(client, assessment);

        mvc.perform(get(path).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isNotFound());
        mvc.perform(post("/links")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\", \"consent\": true, \"termsVersion\": \"v1\"}".formatted(code)))
                .andExpect(status().isCreated());

        mvc.perform(get(path).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        uploadUrl(client(), assessment, "FRONT", "image/jpeg").andExpect(status().isNotFound());
    }

    @Test
    void clienteExcluiAFotoEOArquivoSaiDoBucket(CapturedOutput output) throws Exception {
        String client = client();
        String assessment = assessment(client);
        JsonNode photo = sendPhoto(client, assessment, "FRONT", JPEG);

        mvc.perform(delete("/clients/me/assessments/%s/photos/FRONT".formatted(assessment))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNoContent());

        assertThat(getFile(photo.get("url").asString()).statusCode()).isGreaterThanOrEqualTo(400);
        mvc.perform(get("/clients/me/assessments/%s/photos".formatted(assessment))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(jsonPath("$.length()").value(0));
        // Nenhuma URL assinada nem chave de objeto no log.
        assertThat(output.getAll()).doesNotContain("X-Amz-Signature").doesNotContain("clients/" + client);
    }

    /** A CHECK da V12 recusa pose fora de frente, lado e costas mesmo sem passar pela API. */
    @Test
    void bancoRecusaPoseForaDasTresMesmoPorEscritaDireta() {
        UUID client = UUID.randomUUID();
        UUID assessment = UUID.randomUUID();
        jdbc.sql("""
                        insert into body_assessment (id, client_id, assessment_type, measured_on, weight_kg, author_id,
                            author_role, created_at)
                        values (:id, :client, 'ONLINE', date '2026-11-20', 64.0, :client, 'CLIENT', now())
                        """).param("id", assessment).param("client", client).update();

        assertThatThrownBy(() -> jdbc.sql("""
                                insert into assessment_photo (assessment_id, client_id, pose, object_key, uploaded_at)
                                values (:assessment, :client, 'TOP', 'chave-de-teste', now())
                                """)
                        .param("assessment", assessment)
                        .param("client", client)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_assessment_photo_pose");
    }

    /** Pede a URL e envia o arquivo, sem confirmar; devolve a chave do objeto que ficou no bucket. */
    private String pendingUpload(String client, String assessment, String pose) throws Exception {
        JsonNode upload = read(uploadUrl(client, assessment, pose, "image/jpeg").andExpect(status().isOk()));
        assertThat(putFile(upload.get("uploadUrl").asString(), "image/jpeg", JPEG))
                .isEqualTo(200);
        return "clients/%s/assessments/%s/%s-upload-%s"
                .formatted(
                        client,
                        assessment,
                        pose.toLowerCase(),
                        upload.get("uploadId").asString());
    }

    private String confirmedKey(String assessment, String pose) {
        return jdbc.sql("select object_key from assessment_photo where assessment_id = :id and pose = :pose")
                .param("id", UUID.fromString(assessment))
                .param("pose", pose)
                .query(String.class)
                .single();
    }

    private boolean stored(String key) {
        return storage.describe(key).isPresent();
    }

    /** Direito de exclusão do titular: excluir a avaliação tira do bucket as fotos confirmadas e os envios pendentes. */
    @Test
    void excluirAAvaliacaoApagaDoBucketAsFotosConfirmadasEOsEnviosPendentes() throws Exception {
        String client = client();
        String assessment = assessment(client);
        sendPhoto(client, assessment, "FRONT", JPEG);
        String confirmed = confirmedKey(assessment, "FRONT");
        String pending = pendingUpload(client, assessment, "SIDE");
        assertThat(stored(confirmed)).isTrue();
        assertThat(stored(pending)).isTrue();

        mvc.perform(delete("/clients/me/assessments/" + assessment).header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNoContent());

        assertThat(stored(confirmed)).as("foto confirmada").isFalse();
        assertThat(stored(pending)).as("envio nunca confirmado").isFalse();
    }

    @Test
    void excluirAFotoDaPoseApagaTambemOEnvioPendenteDaPose() throws Exception {
        String client = client();
        String assessment = assessment(client);
        sendPhoto(client, assessment, "FRONT", JPEG);
        String confirmed = confirmedKey(assessment, "FRONT");
        String pending = pendingUpload(client, assessment, "FRONT");
        String otherPose = pendingUpload(client, assessment, "BACK");

        mvc.perform(delete("/clients/me/assessments/%s/photos/FRONT".formatted(assessment))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNoContent());

        assertThat(stored(confirmed)).isFalse();
        assertThat(stored(pending)).isFalse();
        assertThat(stored(otherPose)).as("a outra pose não é tocada").isTrue();
    }

    @Test
    void confirmarApagaOsEnviosPendentesDeTentativasAnterioresDaPose() throws Exception {
        String client = client();
        String assessment = assessment(client);
        String abandoned = pendingUpload(client, assessment, "SIDE");

        sendPhoto(client, assessment, "SIDE", JPEG);

        assertThat(stored(abandoned)).isFalse();
        assertThat(stored(confirmedKey(assessment, "SIDE"))).isTrue();
    }

    @Test
    void expurgoApagaSoOsEnviosNaoConfirmadosMaisAntigosQueOCorte() throws Exception {
        String client = client();
        String assessment = assessment(client);
        sendPhoto(client, assessment, "BACK", JPEG);
        String confirmed = confirmedKey(assessment, "BACK");
        String pending = pendingUpload(client, assessment, "SIDE");

        // Envio recente (dentro da validade da URL) não sai.
        managePhotos.purgeUnconfirmed(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(stored(pending)).isTrue();

        managePhotos.purgeUnconfirmed(Instant.now().plus(1, ChronoUnit.DAYS));
        assertThat(stored(pending)).as("envio nunca confirmado").isFalse();
        assertThat(stored(confirmed)).as("foto confirmada continua").isTrue();
    }

    /**
     * A URL de envio vale 5 minutos e pode ser usada de novo: a foto confirmada não pode mudar por ela, senão um
     * arquivo de qualquer tamanho passaria por cima do limite de 10 MB.
     */
    @Test
    void putDeNovoNaUrlDeEnvioDepoisDeConfirmarNaoMudaAFotoGuardada() throws Exception {
        String client = client();
        String assessment = assessment(client);
        JsonNode upload =
                read(uploadUrl(client, assessment, "FRONT", "image/jpeg").andExpect(status().isOk()));
        String url = upload.get("uploadUrl").asString();
        assertThat(putFile(url, "image/jpeg", JPEG)).isEqualTo(200);
        confirm(client, assessment, "FRONT", upload.get("uploadId").asString()).andExpect(status().isOk());

        assertThat(putFile(url, "image/jpeg", new byte[10 * 1024 * 1024 + 1])).isEqualTo(200);

        String key = confirmedKey(assessment, "FRONT");
        assertThat(storage.describe(key).orElseThrow().sizeBytes()).isEqualTo(JPEG.length);
        JsonNode photos = read(mvc.perform(get("/clients/me/assessments/%s/photos".formatted(assessment))
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk()));
        assertThat(getFile(photos.get(0).get("url").asString()).body()).isEqualTo(JPEG);
    }
}
