package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
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

/** Convites ponta a ponta (DEV-0035): papel, e-mail pelo SMTP de teste, validade, uso único e código pessoal. */
@Import(ClientApiFakes.class)
@ExtendWith(OutputCaptureExtension.class)
class InvitationApiTest extends ServiceIntegrationTest {

    /** SMTP de teste: o mesmo Mailpit do compose de desenvolvimento. */
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.34.0")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forListeningPorts(1025, 8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    /** Conta de profissional já na projeção (o ProfissionalCadastrado chegou). */
    private String personal(String name) {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(
                UUID.fromString(account),
                ProfessionalType.PERSONAL_TRAINER,
                name,
                Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post("/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode created(String account, String body) throws Exception {
        String response = create("personal-" + account, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    private ResultActions lookup(String body) throws Exception {
        return mvc.perform(post("/invitations/lookup")
                .header("Authorization", "Bearer conta-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String tokenOf(JsonNode invitation) {
        String link = invitation.get("link").asString();
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private JsonNode mailTo(String address) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String api = "http://%s:%d/api/v1/".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025));
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode messages = json.readTree(http.send(
                            HttpRequest.newBuilder(URI.create(api + "messages")).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body());
            for (JsonNode message : messages.get("messages")) {
                if (message.get("To").get(0).get("Address").asString().equals(address)) {
                    return json.readTree(http.send(
                                    HttpRequest.newBuilder(URI.create(api + "message/"
                                                    + message.get("ID").asString()))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .body());
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("e-mail não chegou ao SMTP de teste");
    }

    @Test
    void semPapelDeProfissionalNaoCriaConviteNemVeCodigo() throws Exception {
        String account = personal("Paula Personal");

        create("conta-" + account, "{}").andExpect(status().isForbidden());
        mvc.perform(get("/invitations").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isForbidden());
        mvc.perform(get("/professionals/me/invite-code").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isForbidden());
        mvc.perform(post("/invitations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profissionalQueAindaNaoChegouNaProjecaoRecebe409() throws Exception {
        create("personal-" + UUID.randomUUID(), "{}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void conviteComEmailEnviaLinkECodigoSemDadoDoClienteAlemDoEndereco() throws Exception {
        String account = personal("Paula Personal");
        String address = "cliente-" + UUID.randomUUID() + "@exemplo.com";

        JsonNode invitation = created(account, "{\"email\": \"%s\"}".formatted(address));

        assertThat(invitation.get("emailSent").asBoolean()).isTrue();
        assertThat(invitation.get("expiresAt").asString()).isEqualTo("2026-11-30T12:00:00Z");
        JsonNode mail = mailTo(address);
        String text = mail.get("Text").asString();
        assertThat(mail.get("Subject").asString()).isEqualTo("Paula Personal convidou você para o FitHub");
        assertThat(text)
                .contains(invitation.get("link").asString())
                .contains(invitation.get("code").asString())
                .contains("30/11/2026")
                .doesNotContain(address);
    }

    @Test
    void conviteSemEmailDevolveLinkECodigoParaCompartilhar() throws Exception {
        String account = personal("Paula Personal");

        JsonNode invitation = created(account, "{}");

        assertThat(invitation.get("emailSent").asBoolean()).isFalse();
        assertThat(invitation.get("email").isNull()).isTrue();
        assertThat(invitation.get("link").asString()).startsWith("fithub://convite/");
        assertThat(invitation.get("code").asString()).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void bancoGuardaSoOHashDoTokenEDoCodigo() throws Exception {
        String account = personal("Paula Personal");
        JsonNode invitation = created(account, "{}");

        String stored = jdbc.sql("select token_hash || ':' || code_hash from invitation where id = :id")
                .param("id", UUID.fromString(invitation.get("id").asString()))
                .query(String.class)
                .single();

        assertThat(stored)
                .doesNotContain(tokenOf(invitation))
                .doesNotContain(invitation.get("code").asString())
                .matches("[0-9a-f]{64}:[0-9a-f]{64}");
    }

    @Test
    void emailInvalidoVoltaComOCampo() throws Exception {
        create("personal-" + personal("Paula Personal"), "{\"email\": \"sem-arroba\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void consultaPeloLinkOuPeloCodigoMostraQuemConvidaEOQueSeraCompartilhado() throws Exception {
        JsonNode invitation = created(personal("Paula Personal"), "{}");
        String code = invitation.get("code").asString();

        lookup("{\"token\": \"%s\"}".formatted(tokenOf(invitation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("INVITATION"))
                .andExpect(jsonPath("$.professionalName").value("Paula Personal"))
                .andExpect(jsonPath("$.professionalType").value("PERSONAL_TRAINER"))
                .andExpect(jsonPath("$.sharedData.length()").value(4))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-30T12:00:00Z"));
        // Digitado em minúsculas e com hífen.
        lookup("{\"code\": \"%s-%s\"}".formatted(code.substring(0, 4).toLowerCase(), code.substring(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalName").value("Paula Personal"));
        lookup("{\"token\": \"nao-existe\"}").andExpect(status().isNotFound());
        lookup("{\"code\": \"ZZZZ9999\"}").andExpect(status().isNotFound());
    }

    @Test
    void conviteVencidoOuJaAceitoDevolve410() throws Exception {
        String account = personal("Paula Personal");
        JsonNode expired = created(account, "{}");
        JsonNode accepted = created(account, "{}");
        // Criado há 22 dias: venceu em 08/11 (o relógio do teste é 23/11/2026).
        jdbc.sql("""
                        update invitation
                           set created_at = timestamptz '2026-11-01T12:00:00Z',
                               expires_at = timestamptz '2026-11-08T12:00:00Z'
                         where id = :id
                        """).param("id", UUID.fromString(expired.get("id").asString())).update();
        jdbc.sql("update invitation set status = 'ACCEPTED', accepted_at = now() where id = :id")
                .param("id", UUID.fromString(accepted.get("id").asString()))
                .update();

        lookup("{\"token\": \"%s\"}".formatted(tokenOf(expired)))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        lookup("{\"code\": \"%s\"}".formatted(accepted.get("code").asString())).andExpect(status().isGone());
    }

    @Test
    void profissionalListaECancelaOsPropriosConvitesENaoMexeNosDeOutro() throws Exception {
        String account = personal("Paula Personal");
        String other = personal("Pedro Personal");
        JsonNode first = created(account, "{\"email\": \"a-%s@exemplo.com\"}".formatted(UUID.randomUUID()));
        JsonNode second = created(account, "{}");
        created(other, "{}");

        mvc.perform(get("/invitations").header("Authorization", "Bearer personal-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].state").value("PENDING"));

        String secondId = second.get("id").asString();
        mvc.perform(delete("/invitations/" + secondId).header("Authorization", "Bearer personal-" + other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/invitations/" + secondId).header("Authorization", "Bearer personal-" + account))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/invitations/" + secondId).header("Authorization", "Bearer personal-" + account))
                .andExpect(status().isNoContent());

        lookup("{\"token\": \"%s\"}".formatted(tokenOf(second))).andExpect(status().isGone());
        lookup("{\"token\": \"%s\"}".formatted(tokenOf(first))).andExpect(status().isOk());
        mvc.perform(get("/invitations").header("Authorization", "Bearer personal-" + account))
                .andExpect(
                        jsonPath("$[?(@.id == '%s')].state".formatted(secondId)).value("CANCELLED"));
    }

    @Test
    void conviteAceitoNaoSeCancela() throws Exception {
        String account = personal("Paula Personal");
        JsonNode invitation = created(account, "{}");
        jdbc.sql("update invitation set status = 'ACCEPTED', accepted_at = now() where id = :id")
                .param("id", UUID.fromString(invitation.get("id").asString()))
                .update();

        mvc.perform(delete("/invitations/" + invitation.get("id").asString())
                        .header("Authorization", "Bearer personal-" + account))
                .andExpect(status().isConflict());
    }

    @Test
    void profissionalConsultaETrocaOCodigoPessoalEOAntigoDeixaDeValer() throws Exception {
        String account = personal("Paula Personal");

        String code = json.readTree(mvc.perform(get("/professionals/me/invite-code")
                                .header("Authorization", "Bearer personal-" + account))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("code")
                .asString();
        lookup("{\"code\": \"%s\"}".formatted(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PROFESSIONAL_CODE"))
                .andExpect(jsonPath("$.professionalName").value("Paula Personal"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        String rotated = json.readTree(mvc.perform(post("/professionals/me/invite-code")
                                .header("Authorization", "Bearer personal-" + account))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("code")
                .asString();

        assertThat(rotated).isNotEqualTo(code).matches("[A-HJ-NP-Z2-9]{8}");
        lookup("{\"code\": \"%s\"}".formatted(code)).andExpect(status().isNotFound());
        lookup("{\"code\": \"%s\"}".formatted(rotated)).andExpect(status().isOk());
    }

    @Test
    void tokenECodigoNuncaAparecemNoLog(CapturedOutput output) throws Exception {
        String account = personal("Paula Personal");
        JsonNode invitation = created(account, "{\"email\": \"log-%s@exemplo.com\"}".formatted(UUID.randomUUID()));
        lookup("{\"token\": \"%s\"}".formatted(tokenOf(invitation))).andExpect(status().isOk());
        lookup("{\"code\": \"%s\"}".formatted(invitation.get("code").asString()))
                .andExpect(status().isOk());

        assertThat(output.getAll())
                .doesNotContain(tokenOf(invitation))
                .doesNotContain(invitation.get("code").asString());
    }
}
