package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Avaliações corporais (DEV-0044): tipo pela rota, autor, vínculo, histórico e exclusão pelo dono. */
@Import(ClientApiFakes.class)
class AssessmentApiTest extends ServiceIntegrationTest {

    private static final String BODY = """
            {"measuredOn": "2026-11-20", "weightKg": 72.5, "sexForFormulas": "FEMALE",
             "circumferences": {"neck": 33.0, "waist": 70.5, "hip": 98.0}}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

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

    private String linkedPersonal(String client) throws Exception {
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
        mvc.perform(post("/links")
                        .header("Authorization", "Bearer conta-" + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\", \"consent\": true, \"termsVersion\": \"v1\"}".formatted(code)))
                .andExpect(status().isCreated());
        return personal;
    }

    private ResultActions recordOnline(String client, String body) throws Exception {
        return mvc.perform(post("/clients/me/assessments")
                .header("Authorization", "Bearer conta-" + client)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions recordPresential(String token, String client, String body) throws Exception {
        return mvc.perform(post("/professionals/me/clients/" + client + "/assessments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void clienteRegistraAvaliacaoOnlineComoAutor() throws Exception {
        String client = client();

        recordOnline(client, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ONLINE"))
                .andExpect(jsonPath("$.authorRole").value("CLIENT"))
                .andExpect(jsonPath("$.authorId").value(client))
                .andExpect(jsonPath("$.weightKg").value(72.5))
                .andExpect(jsonPath("$.circumferences.waist").value(70.5))
                .andExpect(jsonPath("$.circumferences.chest").doesNotExist());
    }

    @Test
    void profissionalVinculadoRegistraAvaliacaoPresencialEOClienteVe() throws Exception {
        String client = client();
        String personal = linkedPersonal(client);

        recordPresential("personal-" + personal, client, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PRESENTIAL"))
                .andExpect(jsonPath("$.authorRole").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.authorId").value(personal));
        recordOnline(client, BODY.replace("2026-11-20", "2026-11-22")).andExpect(status().isCreated());

        mvc.perform(get("/clients/me/assessments").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].measuredOn").value("2026-11-22"))
                .andExpect(jsonPath("$[1].type").value("PRESENTIAL"));
        mvc.perform(get("/professionals/me/clients/" + client + "/assessments")
                        .header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void profissionalSemVinculoNaoRegistraNemVe() throws Exception {
        String client = client();
        String outsider = UUID.randomUUID().toString();

        recordPresential("personal-" + outsider, client, BODY).andExpect(status().isNotFound());
        mvc.perform(get("/professionals/me/clients/" + client + "/assessments")
                        .header("Authorization", "Bearer personal-" + outsider))
                .andExpect(status().isNotFound());
        recordPresential("conta-" + client, client, BODY).andExpect(status().isForbidden());
    }

    @Test
    void camposInvalidosVoltamComOCampo() throws Exception {
        String client = client();

        recordOnline(client, BODY.replace("2026-11-20", "2026-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("measuredOn"));
        recordOnline(client, BODY.replace("72.5", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("weightKg"));
        recordOnline(client, BODY.replace("70.5", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("circumferences.waist"));
    }

    @Test
    void presencialComDobrasCalculaPollock3E7ComAIdadeDoPerfil() throws Exception {
        String client = client();
        String personal = linkedPersonal(client);

        // Nascida em 24/11/1990, medida em 20/11/2026: 35 anos. Valores conferidos no BodyFatFormulasTest.
        recordPresential("personal-" + personal, client, """
                        {"measuredOn": "2026-11-20", "weightKg": 64.0, "sexForFormulas": "FEMALE",
                         "skinfolds": {"chest": 9, "midaxillary": 11, "triceps": 18, "subscapular": 15, "abdomen": 22,
                                       "suprailiac": 16, "thigh": 26}}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skinfolds.triceps").value(18))
                .andExpect(jsonPath("$.bodyFat[?(@.origin == 'POLLOCK_3')].percent")
                        .value(24.4))
                .andExpect(jsonPath("$.bodyFat[?(@.origin == 'POLLOCK_7')].percent")
                        .value(23.6));
    }

    @Test
    void semOSexoParaAsFormulasAAvaliacaoEGravadaSemOPercentual() throws Exception {
        String client = client();
        String personal = linkedPersonal(client);

        recordPresential("personal-" + personal, client, """
                        {"measuredOn": "2026-11-20", "weightKg": 64.0,
                         "skinfolds": {"triceps": 18, "suprailiac": 16, "thigh": 26}}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyFat.length()").value(0));
    }

    @Test
    void avaliacaoOnlineCompletaEstimaPelaMarinhaComAAlturaDoPerfil() throws Exception {
        // Perfil com 165 cm; mulher com pescoço 33, cintura 70,5 e quadril 98: 26,1% (conferido à parte).
        recordOnline(client(), BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyFat.length()").value(1))
                .andExpect(jsonPath("$.bodyFat[0].origin").value("NAVY"))
                .andExpect(jsonPath("$.bodyFat[0].percent").value(26.1));
    }

    @Test
    void medidasIncompletasOuAvaliacaoPresencialNaoTemEstimativaDaMarinha() throws Exception {
        String client = client();
        recordOnline(client, BODY.replace(", \"hip\": 98.0", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyFat.length()").value(0));
        recordPresential("personal-" + linkedPersonal(client), client, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bodyFat.length()").value(0));
    }

    @Test
    void dobrasSoNaAvaliacaoPresencial() throws Exception {
        recordOnline(client(), """
                        {"measuredOn": "2026-11-20", "weightKg": 64.0, "skinfolds": {"triceps": 18}}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skinfolds"));
    }

    @Test
    void soOClienteExcluiAsProprias() throws Exception {
        String client = client();
        String id = json.readTree(recordOnline(client, BODY)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();

        mvc.perform(delete("/clients/me/assessments/" + id).header("Authorization", "Bearer conta-" + client()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/clients/me/assessments/" + id).header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isNoContent());
        mvc.perform(get("/clients/me/assessments").header("Authorization", "Bearer conta-" + client))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dobraForaDaFaixaVolta400ComOCampo() throws Exception {
        String client = client();
        String personal = linkedPersonal(client);

        recordPresential("personal-" + personal, client, """
                        {"measuredOn": "2026-11-20", "weightKg": 64.0, "skinfolds": {"triceps": 0.5}}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skinfolds.triceps"));
    }

    /** As CHECKs da V9 recusam mesmo sem passar pelo domínio (aprendizado check-com-in-em-coluna-anulavel). */
    @Test
    void bancoRecusaDobraForaDaFaixaEDobraNaOnlineMesmoPorEscritaDireta() {
        String insert = """
                insert into body_assessment (id, client_id, assessment_type, measured_on, weight_kg, author_id,
                    author_role, created_at, skinfold_chest_mm)
                values (:id, :client, :type, date '2026-11-20', 64.0, :author, :role, now(), :chest)
                """;
        UUID client = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("client", client)
                        .param("type", "PRESENTIAL")
                        .param("author", UUID.randomUUID())
                        .param("role", "PROFESSIONAL")
                        .param("chest", new java.math.BigDecimal("0.5"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_body_assessment_skinfolds");
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("client", client)
                        .param("type", "ONLINE")
                        .param("author", client)
                        .param("role", "CLIENT")
                        .param("chest", new java.math.BigDecimal("10"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_body_assessment_skinfolds_presential");
    }
}
