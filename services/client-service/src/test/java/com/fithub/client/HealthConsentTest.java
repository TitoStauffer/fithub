package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.domain.profile.HealthConsents;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Aceite do termo do perfil de saúde (DEV-0179): a base legal do dado de saúde que o próprio cliente informa é o
 * consentimento específico, colhido na primeira gravação e guardado com a versão e o momento.
 */
@Import(ClientApiFakes.class)
class HealthConsentTest extends ServiceIntegrationTest {

    private static final String PROFILE = """
            {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165, "goal": "HEALTH"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    HealthConsents consents;

    private String accountWithProfile() throws Exception {
        String account = UUID.randomUUID().toString();
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE))
                .andExpect(status().isCreated());
        return account;
    }

    private ResultActions save(String account, String consent) throws Exception {
        return mvc.perform(put("/clients/me/health")
                .header("Authorization", "Bearer conta-" + account)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conditions\": [\"DIABETES\"]%s}".formatted(consent)));
    }

    private ResultActions terms(String account) throws Exception {
        return mvc.perform(get("/clients/me/health/terms").header("Authorization", "Bearer conta-" + account));
    }

    private int rows(String table, String account) {
        return jdbc.sql("select count(*) from " + table + " where account_id = :account")
                .param("account", account)
                .query(Integer.class)
                .single();
    }

    @Test
    void withoutTheConsentTheHealthProfileIsRefusedAndNothingIsSaved() throws Exception {
        String account = accountWithProfile();

        save(account, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));
        save(account, ", \"consent\": false, \"termsVersion\": \"v1\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));

        assertThat(rows("client_health", account)).isZero();
        assertThat(rows("health_consent", account)).isZero();
    }

    @Test
    void aConsentForAnotherVersionOfTheTermsIsRefusedWith409() throws Exception {
        String account = accountWithProfile();

        save(account, ", \"consent\": true, \"termsVersion\": \"v0\"")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Termo desatualizado"));

        assertThat(rows("client_health", account)).isZero();
        assertThat(rows("health_consent", account)).isZero();
    }

    @Test
    void theFirstSaveRecordsTheConsentWithVersionAndMomentOnceAndTheNextOnesNeedNoConsent() throws Exception {
        String account = accountWithProfile();
        terms(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsVersion").value("v1"))
                .andExpect(jsonPath("$.termsText").isNotEmpty())
                .andExpect(jsonPath("$.accepted").value(false));

        save(account, ", \"consent\": true, \"termsVersion\": \"v1\"").andExpect(status().isOk());
        save(account, "").andExpect(status().isOk());
        save(account, ", \"consent\": true, \"termsVersion\": \"v1\"").andExpect(status().isOk());

        assertThat(jdbc.sql("select terms_version || '|' || granted_at from health_consent where account_id = :account")
                        .param("account", account)
                        .query(String.class)
                        .list())
                .singleElement()
                .asString()
                .startsWith("v1|2026-11-23 12:00:00");
        terms(account).andExpect(jsonPath("$.accepted").value(true));
    }

    // Dois envios simultâneos chegariam ao repositório com a mesma conta e versão: o segundo não pode virar erro.
    @Test
    void theSameConsentRecordedTwiceStaysOneRowWithTheFirstMoment() throws Exception {
        String account = accountWithProfile();

        consents.accept(account, "v1", java.time.Instant.parse("2026-11-23T12:00:00Z"));
        consents.accept(account, "v1", java.time.Instant.parse("2026-11-23T12:05:00Z"));

        assertThat(jdbc.sql("select granted_at from health_consent where account_id = :account")
                        .param("account", account)
                        .query(java.time.OffsetDateTime.class)
                        .list())
                .singleElement()
                .satisfies(at -> assertThat(at.toInstant()).isEqualTo(java.time.Instant.parse("2026-11-23T12:00:00Z")));
    }

    @Test
    void theConsentOfOneAccountDoesNotCountForAnother() throws Exception {
        String first = accountWithProfile();
        String second = accountWithProfile();
        save(first, ", \"consent\": true, \"termsVersion\": \"v1\"").andExpect(status().isOk());

        save(second, "").andExpect(status().isBadRequest());
        terms(second).andExpect(jsonPath("$.accepted").value(false));
    }
}
