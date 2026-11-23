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

/** Encerramento e listas de vínculos (DEV-0037): partes, estado, idempotência e listas. */
@Import(ClientApiFakes.class)
class LinkEndingTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    private String client(String name) throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
                                 "goal": "HEALTH"}
                                """.formatted(name)))
                .andExpect(status().isCreated());
        return account;
    }

    private String professional(ProfessionalType type, String name) {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(UUID.fromString(account), type, name, Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    /** Vínculo ativo pelo código pessoal do profissional; devolve o id do vínculo. */
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

    private ResultActions end(String token, String linkId) throws Exception {
        return mvc.perform(post("/links/" + linkId + "/end").header("Authorization", "Bearer " + token));
    }

    private String statusOf(String linkId) {
        return jdbc.sql("select status || ':' || coalesce(ended_by, '-') from client_link where id = :id")
                .param("id", UUID.fromString(linkId))
                .query(String.class)
                .single();
    }

    @Test
    void clienteEncerraOProprioVinculoNaHoraComDataEAutor() throws Exception {
        String client = client("Ana");
        String linkId = link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));

        end("conta-" + client, linkId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endedBy").value("CLIENT"))
                .andExpect(jsonPath("$.endedAt").value("2026-11-23T12:00:00Z"));

        assertThat(statusOf(linkId)).isEqualTo("ENDED:CLIENT");
    }

    @Test
    void profissionalEncerraOVinculoEmQueEParte() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String linkId = link(client("Ana"), personal);

        end("personal-" + personal, linkId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedBy").value("PROFESSIONAL"));

        assertThat(statusOf(linkId)).isEqualTo("ENDED:PROFESSIONAL");
    }

    @Test
    void ninguemEncerraVinculoAlheio() throws Exception {
        String linkId = link(client("Ana"), professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));
        String outsider = professional(ProfessionalType.PERSONAL_TRAINER, "Pedro");

        end("conta-" + client("Bia"), linkId).andExpect(status().isNotFound());
        end("personal-" + outsider, linkId).andExpect(status().isNotFound());
        end("conta-" + UUID.randomUUID(), UUID.randomUUID().toString()).andExpect(status().isNotFound());

        assertThat(statusOf(linkId)).isEqualTo("ACTIVE:-");
    }

    @Test
    void encerrarDeNovoNaoMudaNadaEValeOPrimeiroEncerramento() throws Exception {
        String client = client("Ana");
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String linkId = link(client, personal);
        end("conta-" + client, linkId).andExpect(status().isOk());

        end("personal-" + personal, linkId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endedBy").value("CLIENT"));

        assertThat(statusOf(linkId)).isEqualTo("ENDED:CLIENT");
    }

    @Test
    void encerramentosSimultaneosDasDuasPartesValemOPrimeiro() throws Exception {
        // Várias rodadas para a corrida acontecer de fato: as duas respostas e o banco contam a mesma história.
        for (int round = 0; round < 8; round++) {
            String client = client("Ana");
            String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
            String linkId = link(client, personal);
            java.util.List<java.util.concurrent.Callable<String>> calls = java.util.List.of(
                    () -> endedBy("conta-" + client, linkId), () -> endedBy("personal-" + personal, linkId));

            java.util.List<String> answers = new java.util.ArrayList<>();
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                for (var answer : pool.invokeAll(calls)) {
                    answers.add(answer.get());
                }
            }

            assertThat(answers.get(0)).isEqualTo(answers.get(1));
            assertThat(statusOf(linkId)).isEqualTo("ENDED:" + answers.get(0));
        }
    }

    private String endedBy(String token, String linkId) throws Exception {
        return json.readTree(end(token, linkId)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("endedBy")
                .asString();
    }

    @Test
    void vinculoEncerradoNaoVoltaEOClientePodeAceitarOutroDoMesmoTipo() throws Exception {
        String client = client("Ana");
        String first = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String linkId = link(client, first);
        end("conta-" + client, linkId).andExpect(status().isOk());

        // BR-07: depois de encerrar, aceitar outro personal é permitido; o encerrado continua encerrado.
        link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Pedro"));
        String again = link(client, professional(ProfessionalType.NUTRITIONIST, "Nina"));

        assertThat(statusOf(linkId)).isEqualTo("ENDED:CLIENT");
        assertThat(statusOf(again)).isEqualTo("ACTIVE:-");
    }

    @Test
    void clienteListaOsVinculosAtivosEEncerradosComONomeDoProfissional() throws Exception {
        String client = client("Ana");
        String ended = link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));
        end("conta-" + client, ended).andExpect(status().isOk());
        link(client, professional(ProfessionalType.NUTRITIONIST, "Nina"));

        mvc.perform(get("/links").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[?(@.id == '%s')].status".formatted(ended)).value("ENDED"))
                .andExpect(jsonPath("$[?(@.professionalName == 'Nina')].status").value("ACTIVE"));
        mvc.perform(get("/links").header("Authorization", "Bearer conta-" + client("Bia")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void profissionalListaSoOsClientesComVinculoAtivo() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String ana = client("Ana");
        String bia = client("Bia");
        link(ana, personal);
        String biaLink = link(bia, personal);
        end("conta-" + bia, biaLink).andExpect(status().isOk());

        mvc.perform(get("/professionals/me/clients").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientId").value(ana))
                .andExpect(jsonPath("$[0].clientName").value("Ana"));
        mvc.perform(get("/professionals/me/clients").header("Authorization", "Bearer conta-" + ana))
                .andExpect(status().isForbidden());
    }
}
