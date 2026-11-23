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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Leitura do perfil e da saúde pelo profissional vinculado (DEV-0040): vínculo, BR-04 e auditoria. */
@Import(ClientApiFakes.class)
class ProfessionalReadTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    /** Cliente com perfil e perfil de saúde. */
    private String client() throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HYPERTROPHY"}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(put("/clients/me/health")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditions\": [\"HYPERTENSION\"], \"notes\": \"joelho\"}"))
                .andExpect(status().isOk());
        return account;
    }

    private String professional(ProfessionalType type) {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(UUID.fromString(account), type, "Prof", Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    private String link(String client, String professional) throws Exception {
        String code = jdbc.sql("select invite_code from professional where id = :id")
                .param("id", UUID.fromString(professional))
                .query(String.class)
                .single();
        return json.readTree(mvc.perform(post("/links")
                                .header("Authorization", "Bearer conta-" + client)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"%s\", \"consent\": true, \"termsVersion\": \"v1\"}"
                                        .formatted(code)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    private ResultActions read(String token, String client, String what) throws Exception {
        return mvc.perform(
                get("/professionals/me/clients/" + client + "/" + what).header("Authorization", "Bearer " + token));
    }

    private int auditRows(String professional, String client) {
        return jdbc.sql("select count(*) from health_access_log where professional_id = :p and client_id = :c")
                .param("p", UUID.fromString(professional))
                .param("c", UUID.fromString(client))
                .query(Integer.class)
                .single();
    }

    @Test
    void personalENutricionistaVinculadosLeemPerfilESaude() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER);
        String nutri = professional(ProfessionalType.NUTRITIONIST);
        link(client, personal);
        link(client, nutri);

        read("personal-" + personal, client, "profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Cliente"))
                .andExpect(jsonPath("$.age").value(35))
                .andExpect(jsonPath("$.goal").value("HYPERTROPHY"));
        read("nutri-" + nutri, client, "health")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions[0]").value("HYPERTENSION"))
                .andExpect(jsonPath("$.notes").value("joelho"));
        read("personal-" + personal, client, "health").andExpect(status().isOk());
    }

    @Test
    void semVinculoComVinculoEncerradoOuDeOutroProfissionalDevolve404() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER);
        String linkId = link(client, personal);
        String outsider = professional(ProfessionalType.PERSONAL_TRAINER);

        read("personal-" + outsider, client, "profile").andExpect(status().isNotFound());
        read("personal-" + outsider, client, "health").andExpect(status().isNotFound());
        read("personal-" + personal, UUID.randomUUID().toString(), "profile").andExpect(status().isNotFound());

        mvc.perform(post("/links/" + linkId + "/end").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());

        read("personal-" + personal, client, "profile").andExpect(status().isNotFound());
        read("personal-" + personal, client, "health").andExpect(status().isNotFound());
        assertThat(auditRows(outsider, client)).isZero();
    }

    @Test
    void contaSemPapelDeProfissionalNaoLe() throws Exception {
        String client = client();
        read("conta-" + UUID.randomUUID(), client, "profile").andExpect(status().isForbidden());
    }

    @Test
    void cadaLeituraDaSaudeFicaRegistradaSemOConteudo() throws Exception {
        String client = client();
        String nutri = professional(ProfessionalType.NUTRITIONIST);
        link(client, nutri);

        read("nutri-" + nutri, client, "health").andExpect(status().isOk());
        read("nutri-" + nutri, client, "health").andExpect(status().isOk());
        read("nutri-" + nutri, client, "profile").andExpect(status().isOk());

        // Duas leituras da saúde, duas linhas; a do perfil não entra. A tabela não tem coluna para o dado lido.
        assertThat(auditRows(nutri, client)).isEqualTo(2);
        assertThat(jdbc.sql("select accessed_at::text from health_access_log where client_id = :c limit 1")
                        .param("c", UUID.fromString(client))
                        .query(String.class)
                        .single())
                .startsWith("2026-11-23 12:00:00");
        assertThat(jdbc.sql("""
                        select string_agg(column_name, ',' order by column_name) from information_schema.columns
                         where table_name = 'health_access_log'
                        """).query(String.class).single()).isEqualTo("accessed_at,client_id,id,professional_id");
    }
}
