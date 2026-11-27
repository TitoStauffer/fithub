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

/**
 * Novo aceite do termo num vínculo existente (DEV-0160): vínculo aceito numa versão anterior aparece com o consentimento
 * desatualizado, e o cliente aceita a versão vigente sem refazer o vínculo.
 */
@Import(ClientApiFakes.class)
class LinkConsentRenewalTest extends ServiceIntegrationTest {

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
                                .content("{\"code\": \"%s\", \"consent\": true, \"termsVersion\": \"v2\"}"
                                        .formatted(code)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    private ResultActions renew(String token, String linkId, String consent, String version) throws Exception {
        return mvc.perform(post("/links/" + linkId + "/consent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consent\": %s, \"termsVersion\": \"%s\"}".formatted(consent, version)));
    }

    /** Simula um vínculo aceito antes da v2: o aceite gravado passa a ser da v1, um dia antes (o relógio do teste é fixo). */
    private void acceptedOnV1(String linkId) {
        jdbc.sql(
                        "update consent set terms_version = 'v1', granted_at = granted_at - interval '1 day' where link_id = :id")
                .param("id", UUID.fromString(linkId))
                .update();
    }

    private long consents(String linkId) {
        return jdbc.sql("select count(*) from consent where link_id = :id")
                .param("id", UUID.fromString(linkId))
                .query(Long.class)
                .single();
    }

    private ResultActions mine(String client) throws Exception {
        return mvc.perform(get("/links").header("Authorization", "Bearer conta-" + client));
    }

    @Test
    void aNewLinkRecordsTheCurrentTermsAndIsNotOutdated() throws Exception {
        String client = client("Ana");
        link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));

        mine(client)
                .andExpect(jsonPath("$[0].consentVersion").value("v2"))
                .andExpect(jsonPath("$[0].consentOutdated").value(false));
    }

    @Test
    void aLinkAcceptedOnAnOlderVersionIsOutdatedUntilTheClientAcceptsTheCurrentOne() throws Exception {
        String client = client("Ana");
        String linkId = link(client, professional(ProfessionalType.NUTRITIONIST, "Nina"));
        acceptedOnV1(linkId);
        mine(client)
                .andExpect(jsonPath("$[0].consentVersion").value("v1"))
                .andExpect(jsonPath("$[0].consentOutdated").value(true));

        renew("conta-" + client, linkId, "true", "v2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(linkId))
                .andExpect(jsonPath("$.consentVersion").value("v2"));

        mine(client)
                .andExpect(jsonPath("$[0].consentVersion").value("v2"))
                .andExpect(jsonPath("$[0].consentOutdated").value(false));
        // O aceite anterior continua no histórico (NFR-01).
        assertThat(consents(linkId)).isEqualTo(2);
    }

    @Test
    void acceptingTheCurrentVersionAgainRecordsNothing() throws Exception {
        String client = client("Ana");
        String linkId = link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));

        renew("conta-" + client, linkId, "true", "v2").andExpect(status().isOk());

        assertThat(consents(linkId)).isEqualTo(1);
    }

    @Test
    void theRenewalNeedsExplicitConsentAndTheVersionTheScreenShowed() throws Exception {
        String client = client("Ana");
        String linkId = link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));
        acceptedOnV1(linkId);

        renew("conta-" + client, linkId, "false", "v2").andExpect(status().isBadRequest());
        renew("conta-" + client, linkId, "null", "v2").andExpect(status().isBadRequest());
        renew("conta-" + client, linkId, "true", "v1").andExpect(status().isConflict());

        assertThat(consents(linkId)).isEqualTo(1);
    }

    @Test
    void onlyTheClientOfTheLinkRenewsTheConsent() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String linkId = link(client("Ana"), personal);
        acceptedOnV1(linkId);

        renew("conta-" + client("Bia"), linkId, "true", "v2").andExpect(status().isNotFound());
        renew("personal-" + personal, linkId, "true", "v2").andExpect(status().isNotFound());
        renew("conta-" + client("Caio"), UUID.randomUUID().toString(), "true", "v2")
                .andExpect(status().isNotFound());

        assertThat(consents(linkId)).isEqualTo(1);
    }

    @Test
    void anEndedLinkDoesNotReceiveANewConsent() throws Exception {
        String client = client("Ana");
        String linkId = link(client, professional(ProfessionalType.PERSONAL_TRAINER, "Paula"));
        acceptedOnV1(linkId);
        mvc.perform(post("/links/" + linkId + "/end").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());

        renew("conta-" + client, linkId, "true", "v2").andExpect(status().isConflict());
        // Encerrado, ele não aparece como desatualizado: não há o que aceitar.
        mine(client).andExpect(jsonPath("$[0].consentOutdated").value(false));
        assertThat(consents(linkId)).isEqualTo(1);
    }
}
