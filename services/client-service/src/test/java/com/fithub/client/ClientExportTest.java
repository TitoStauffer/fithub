package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportação dos dados do cliente no client-service (DEV-0104): tudo do cliente do token, vínculos encerrados com o
 * consentimento, leituras da saúde sem o conteúdo, nada de outro cliente e só para o papel de cliente.
 */
@Import(ClientApiFakes.class)
class ClientExportTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    private String professional(ProfessionalType type, String name) {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(UUID.fromString(account), type, name, Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private String token(ProfessionalType type) {
        return type == ProfessionalType.NUTRITIONIST ? "nutri-" : "personal-";
    }

    private String link(String client, String professional, ProfessionalType type) throws Exception {
        String invitation = json.readTree(mvc.perform(post("/invitations")
                                .header("Authorization", "Bearer " + token(type) + professional)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("link")
                .asString();
        return json.readTree(mvc.perform(post("/links")
                                .header("Authorization", "Bearer conta-" + client)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\": \"%s\", \"consent\": true, \"termsVersion\": \"v2\"}"
                                        .formatted(invitation.substring(invitation.lastIndexOf('/') + 1))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    /** Cliente com perfil, saúde, uma avaliação, um vínculo ativo e um encerrado, e a saúde lida pelo personal. */
    private String fullClient(String personal, String nutritionist, String notes) throws Exception {
        String client = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(put("/clients/me/health")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditions\": [\"HYPERTENSION\"], \"notes\": \"" + notes + "\"}"))
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
        link(client, personal, ProfessionalType.PERSONAL_TRAINER);
        String ended = link(client, nutritionist, ProfessionalType.NUTRITIONIST);
        mvc.perform(get("/professionals/me/clients/" + client + "/health")
                        .header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        mvc.perform(post("/links/" + ended + "/end").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());
        return client;
    }

    @Test
    void exportaTudoDoClienteComOsVinculosOConsentimentoEAsLeiturasDaSaude() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula Personal");
        String nutritionist = professional(ProfessionalType.NUTRITIONIST, "Nina Nutri");
        String client = fullClient(personal, nutritionist, "joelho");
        fullClient(personal, nutritionist, "outra pessoa");

        mvc.perform(get("/clients/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").value("2026-11-23T12:00:00Z"))
                .andExpect(jsonPath("$.profile.name").value("Ana Cliente"))
                .andExpect(jsonPath("$.health.notes").value("joelho"))
                .andExpect(jsonPath("$.health.conditions[0]").value("HYPERTENSION"))
                .andExpect(jsonPath("$.assessments.length()").value(1))
                .andExpect(jsonPath("$.assessments[0].assessment.weightKg").value(64.0))
                .andExpect(jsonPath("$.assessments[0].photos.length()").value(1))
                .andExpect(jsonPath("$.assessments[0].photos[0].pose").value("FRONT"))
                .andExpect(jsonPath("$.assessments[0].photos[0].url").isNotEmpty())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[?(@.professionalName == 'Nina Nutri')].status")
                        .value("ENDED"))
                .andExpect(jsonPath("$.links[?(@.professionalName == 'Nina Nutri')].endedBy")
                        .value("CLIENT"))
                .andExpect(jsonPath("$.links[?(@.professionalName == 'Nina Nutri')].consent.termsVersion")
                        .value("v2"))
                .andExpect(jsonPath("$.links[?(@.professionalName == 'Paula Personal')].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.links[?(@.professionalName == 'Paula Personal')].consent.scope.length()")
                        .value(4))
                .andExpect(jsonPath("$.healthReads.length()").value(1))
                .andExpect(jsonPath("$.healthReads[0].professionalName").value("Paula Personal"))
                .andExpect(jsonPath("$.healthReads[0].professionalId").value(personal));
    }

    @Test
    void contaSemNadaExportaListasVaziasSemPerfilNemSaude() throws Exception {
        String body = mvc.perform(
                        get("/clients/me/export").header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessments.length()").value(0))
                .andExpect(jsonPath("$.links.length()").value(0))
                .andExpect(jsonPath("$.healthReads.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Campo que pode faltar é omitido, não null: é o que o OpenAPI promete (aprendizado nulo-sobre-ref-no-openapi).
        assertThat(body)
                .doesNotContain("\"profile\"")
                .doesNotContain("\"health\"")
                .doesNotContain("null");
    }

    @Test
    void vinculoAtivoSaiSemDataNemAutorDoEncerramento() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula Personal");
        String nutritionist = professional(ProfessionalType.NUTRITIONIST, "Nina Nutri");
        String client = fullClient(personal, nutritionist, "joelho");

        String body = mvc.perform(get("/clients/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int active = 0;
        for (var link : json.readTree(body).get("links")) {
            if ("ACTIVE".equals(link.get("status").asString())) {
                active++;
                assertThat(link.has("endedAt")).isFalse();
                assertThat(link.has("endedBy")).isFalse();
            }
        }
        assertThat(active).isEqualTo(1);
    }

    @Test
    void semTokenOuSemOPapelDeClienteNaoExporta() throws Exception {
        mvc.perform(get("/clients/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/clients/me/export").header("Authorization", "Bearer sempapel-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
