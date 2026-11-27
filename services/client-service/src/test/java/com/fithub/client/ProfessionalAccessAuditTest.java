package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.AccessLogRetention;
import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auditoria das leituras de dados sensíveis pelo profissional (DEV-0106, NFR-03, ADR-012): saúde, avaliações, série
 * corporal e fotos ficam registradas com o recurso; a leitura do próprio cliente não; o expurgo tira o que passou de 6
 * meses; a exportação traz o registro completo.
 */
@Import(ClientApiFakes.class)
class ProfessionalAccessAuditTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    @Autowired
    AccessLogRetention retention;

    /** Cliente com perfil e uma avaliação, vinculado ao personal; devolve o id da avaliação. */
    private String linkedClientWithAssessment(String client, String personal) throws Exception {
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """))
                .andExpect(status().isCreated());
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
        String invitation = json.readTree(mvc.perform(post("/invitations")
                                .header("Authorization", "Bearer personal-" + personal)
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
                                .formatted(invitation.substring(invitation.lastIndexOf('/') + 1))))
                .andExpect(status().isCreated());
        return assessment;
    }

    private String personal() {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(
                UUID.fromString(account),
                ProfessionalType.PERSONAL_TRAINER,
                "Paula Personal",
                Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private List<String> resources(String client) {
        return jdbc.sql("select resource from health_access_log where client_id = :c order by resource")
                .param("c", UUID.fromString(client))
                .query(String.class)
                .list();
    }

    @Test
    void leiturasDeAvaliacoesSerieEFotosPeloProfissionalFicamRegistradasComORecurso() throws Exception {
        String client = UUID.randomUUID().toString();
        String personal = personal();
        String assessment = linkedClientWithAssessment(client, personal);
        String base = "/professionals/me/clients/" + client + "/assessments";

        mvc.perform(get(base).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/series").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/" + assessment + "/photos").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());

        assertThat(resources(client)).containsExactly("ASSESSMENTS", "ASSESSMENTS", "PHOTOS");
        assertThat(jdbc.sql("select count(distinct professional_id) from health_access_log where client_id = :c")
                        .param("c", UUID.fromString(client))
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void leituraDoProprioClienteNaoFicaRegistrada() throws Exception {
        String client = UUID.randomUUID().toString();
        String assessment = linkedClientWithAssessment(client, personal());

        mvc.perform(get("/clients/me/assessments").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());
        mvc.perform(get("/clients/me/assessments/series").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());
        mvc.perform(get("/clients/me/assessments/" + assessment + "/photos")
                        .header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());

        assertThat(resources(client)).isEmpty();
    }

    @Test
    void aExportacaoTrazORegistroCompletoEOHealthReadsSoASaude() throws Exception {
        String client = UUID.randomUUID().toString();
        String personal = personal();
        linkedClientWithAssessment(client, personal);
        mvc.perform(get("/professionals/me/clients/" + client + "/assessments")
                        .header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());

        mvc.perform(get("/clients/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLog.length()").value(1))
                .andExpect(jsonPath("$.accessLog[0].resource").value("ASSESSMENTS"))
                .andExpect(jsonPath("$.accessLog[0].professionalName").value("Paula Personal"))
                .andExpect(jsonPath("$.healthReads.length()").value(0));
    }

    @Test
    void expurgoTiraSoOQuePassouDe6Meses() {
        // No teste, agora é 23/11/2026 12:00 UTC: 6 meses antes é 23/05/2026 12:00.
        UUID old = insert("2026-05-23T11:59:59Z");
        UUID exactly = insert("2026-05-23T12:00:00Z");
        UUID recent = insert("2026-11-01T12:00:00Z");

        int removed = retention.purge();

        assertThat(removed).isEqualTo(1);
        assertThat(exists(old)).isFalse();
        assertThat(exists(exactly)).isTrue();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    void bancoRecusaRecursoForaDaLista() {
        assertThatThrownBy(() -> jdbc.sql("""
                                insert into health_access_log (id, professional_id, client_id, resource, accessed_at)
                                values (:id, :p, :c, 'NOTES', now())
                                """)
                        .param("id", UUID.randomUUID())
                        .param("p", UUID.randomUUID())
                        .param("c", UUID.randomUUID())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(String at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into health_access_log (id, professional_id, client_id, resource, accessed_at)
                        values (:id, :p, :c, 'HEALTH', :at)
                        """)
                .param("id", id)
                .param("p", UUID.randomUUID())
                .param("c", UUID.randomUUID())
                .param("at", Timestamp.from(Instant.parse(at)))
                .update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from health_access_log where id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }
}
