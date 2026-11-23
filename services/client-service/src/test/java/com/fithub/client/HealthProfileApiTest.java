package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Perfil de saúde ponta a ponta (DEV-0033): HTTP, validação e Postgres real. */
@Import(ClientApiFakes.class)
@ExtendWith(OutputCaptureExtension.class)
class HealthProfileApiTest extends ServiceIntegrationTest {

    /** Marca no texto livre: se aparecer na saída do serviço, um dado de saúde foi para log. */
    private static final String MARKER = "marca-de-saude-7f3a";

    private static final String PROFILE = """
            {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
             "goal": "HEALTH"}
            """;

    private static final String HEALTH = """
            {"conditions": ["HYPERTENSION", "DIABETES", "HYPERTENSION"], "notes": "  %s  "}
            """.formatted(MARKER);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private static String newAccount() {
        return UUID.randomUUID().toString();
    }

    private String accountWithProfile() throws Exception {
        String account = newAccount();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE))
                .andExpect(status().isCreated());
        return account;
    }

    private ResultActions save(String account, String body) throws Exception {
        return mvc.perform(put("/clients/me/health")
                .header("Authorization", "Bearer conta-" + account)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions read(String account) throws Exception {
        return mvc.perform(get("/clients/me/health").header("Authorization", "Bearer conta-" + account));
    }

    private int healthRows(String account) {
        return jdbc.sql("select count(*) from client_health where account_id = :account")
                .param("account", account)
                .query(Integer.class)
                .single();
    }

    @Test
    void semTokenASaudeERecusada() throws Exception {
        mvc.perform(put("/clients/me/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HEALTH))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/clients/me/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void semOPerfilDoClienteASaudeERecusadaCom409() throws Exception {
        String account = newAccount();

        save(account, HEALTH)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Crie seu perfil antes de informar os dados de saúde."));
        assertThat(healthRows(account)).isZero();
    }

    @Test
    void gravaELeASaudeComCondicoesSemRepeticao() throws Exception {
        String account = accountWithProfile();
        read(account)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Esta conta ainda não informou os dados de saúde."));

        save(account, HEALTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions.length()").value(2))
                .andExpect(jsonPath("$.conditions[0]").value("DIABETES"))
                .andExpect(jsonPath("$.conditions[1]").value("HYPERTENSION"))
                .andExpect(jsonPath("$.notes").value(MARKER))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-23T12:00:00Z"));

        read(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions.length()").value(2))
                .andExpect(jsonPath("$.notes").value(MARKER));
    }

    @Test
    void novaGravacaoSubstituiAAnteriorSemCriarOutra() throws Exception {
        String account = accountWithProfile();
        save(account, HEALTH).andExpect(status().isOk());

        save(account, """
                        {"conditions": []}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions.length()").value(0))
                .andExpect(jsonPath("$.notes").doesNotExist());

        assertThat(healthRows(account)).isEqualTo(1);
    }

    @Test
    void cadaContaSoVeAPropriaSaudeEAContaVemDoToken() throws Exception {
        String account = accountWithProfile();
        String other = accountWithProfile();

        save(account, HEALTH.replace("\"notes\"", "\"accountId\": \"%s\", \"notes\"".formatted(other)))
                .andExpect(status().isOk());

        assertThat(healthRows(account)).isEqualTo(1);
        assertThat(healthRows(other)).isZero();
        read(other).andExpect(status().isNotFound());
    }

    @Test
    void camposInvalidosVoltamComOCampoSemRepetirODadoDeSaude() throws Exception {
        String account = accountWithProfile();

        save(account, """
                        {"conditions": [], "notes": "%s"}
                        """.formatted(MARKER.repeat(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("notes"))
                .andExpect(content().string(not(containsString(MARKER))));
        save(account, """
                        {"notes": "sem condições"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("conditions"));
        save(account, """
                        {"conditions": ["DIABETES", null]}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("conditions"));
        save(account, """
                        {"conditions": ["GRIPE"]}
                        """).andExpect(status().isBadRequest());

        assertThat(healthRows(account)).isZero();
    }

    @Test
    void oDadoDeSaudeNuncaApareceNoLog(CapturedOutput output) throws Exception {
        String account = accountWithProfile();

        save(account, HEALTH).andExpect(status().isOk());
        read(account).andExpect(status().isOk());
        save(account, """
                        {"conditions": [], "notes": "%s"}
                        """.formatted(MARKER.repeat(60))).andExpect(status().isBadRequest());
        save(newAccount(), HEALTH).andExpect(status().isConflict());

        assertThat(output.getAll()).doesNotContain(MARKER);
    }
}
