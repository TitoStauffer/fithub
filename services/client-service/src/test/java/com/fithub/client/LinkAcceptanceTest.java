package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.client.application.RecordProfessional;
import com.fithub.client.domain.professional.ProfessionalType;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Aceite do convite com consentimento (DEV-0036): cada regra, concorrência e as garantias do banco. */
@Import(ClientApiFakes.class)
class LinkAcceptanceTest extends ServiceIntegrationTest {

    private static final String PROFILE = """
            {"name": "Ana Cliente", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165, "goal": "HEALTH"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordProfessional recordProfessional;

    /** Conta com o perfil de cliente criado. */
    private String client() throws Exception {
        String account = UUID.randomUUID().toString();
        withProfile(account);
        return account;
    }

    private void withProfile(String account) throws Exception {
        mvc.perform(put("/clients/me/profile")
                        .header("Authorization", "Bearer conta-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE))
                .andExpect(status().isCreated());
    }

    private String professional(ProfessionalType type, String name) {
        String account = UUID.randomUUID().toString();
        recordProfessional.registered(UUID.fromString(account), type, name, Instant.parse("2026-11-20T10:00:00Z"));
        return account;
    }

    /** Convite criado pelo profissional: devolve o JSON com link e código. */
    private JsonNode invite(String professional, ProfessionalType type) throws Exception {
        String prefix = type == ProfessionalType.NUTRITIONIST ? "nutri-" : "personal-";
        return json.readTree(mvc.perform(post("/invitations")
                        .header("Authorization", "Bearer " + prefix + professional)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private static String tokenOf(JsonNode invitation) {
        String link = invitation.get("link").asString();
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private ResultActions accept(String client, String target, String consent, String version) throws Exception {
        return mvc.perform(post("/links")
                .header("Authorization", "Bearer conta-" + client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{%s, \"consent\": %s, \"termsVersion\": \"%s\"}".formatted(target, consent, version)));
    }

    private ResultActions acceptToken(String client, JsonNode invitation) throws Exception {
        return accept(client, "\"token\": \"%s\"".formatted(tokenOf(invitation)), "true", "v1");
    }

    private int count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    private int activeLinks(String client) {
        return count(
                "select count(*) from client_link where client_id = :id and status = 'ACTIVE'",
                UUID.fromString(client));
    }

    private int consents(String client) {
        return count("select count(*) from consent where client_id = :id", UUID.fromString(client));
    }

    private String invitationStatus(JsonNode invitation) {
        return jdbc.sql("select status from invitation where id = :id")
                .param("id", UUID.fromString(invitation.get("id").asString()))
                .query(String.class)
                .single();
    }

    @Test
    void aceitePeloLinkCriaOVinculoEGravaOConsentimentoComDataEscopoEVersao() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula Personal");
        JsonNode invitation = invite(personal, ProfessionalType.PERSONAL_TRAINER);

        acceptToken(client, invitation)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalId").value(personal))
                .andExpect(jsonPath("$.professionalName").value("Paula Personal"))
                .andExpect(jsonPath("$.professionalType").value("PERSONAL_TRAINER"))
                .andExpect(jsonPath("$.sharedData.length()").value(4))
                .andExpect(jsonPath("$.startedAt").value("2026-11-23T12:00:00Z"));

        assertThat(activeLinks(client)).isEqualTo(1);
        assertThat(invitationStatus(invitation)).isEqualTo("ACCEPTED");
        String consent = jdbc.sql("""
                        select c.terms_version || '|' || array_to_string(c.scope, ',') || '|' || c.granted_at
                          from consent c join client_link l on l.id = c.link_id
                         where c.client_id = :id and l.invitation_id = :invitation
                        """)
                .param("id", UUID.fromString(client))
                .param("invitation", UUID.fromString(invitation.get("id").asString()))
                .query(String.class)
                .single();
        assertThat(consent).startsWith("v1|PROFILE,HEALTH,ASSESSMENTS,PHOTOS|2026-11-23 12:00:00");
    }

    @Test
    void aceitePeloCodigoDoConviteOuPeloCodigoPessoalDoProfissional() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula Personal");
        String nutri = professional(ProfessionalType.NUTRITIONIST, "Nina Nutri");
        JsonNode invitation = invite(personal, ProfessionalType.PERSONAL_TRAINER);
        String personalCode = jdbc.sql("select invite_code from professional where id = :id")
                .param("id", UUID.fromString(nutri))
                .query(String.class)
                .single();

        accept(client, "\"code\": \"%s\"".formatted(invitation.get("code").asString()), "true", "v1")
                .andExpect(status().isCreated());
        accept(client, "\"code\": \"%s\"".formatted(personalCode.toLowerCase()), "true", "v1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalType").value("NUTRITIONIST"));

        // Um personal e um nutricionista ativos ao mesmo tempo (FR-13); o do código pessoal não tem convite.
        assertThat(activeLinks(client)).isEqualTo(2);
        assertThat(count(
                        "select count(*) from client_link where client_id = :id and invitation_id is null",
                        UUID.fromString(client)))
                .isEqualTo(1);
    }

    @Test
    void semConsentimentoExplicitoNaoCriaVinculoNemConsentimento() throws Exception {
        String client = client();
        JsonNode invitation =
                invite(professional(ProfessionalType.PERSONAL_TRAINER, "Paula"), ProfessionalType.PERSONAL_TRAINER);
        String target = "\"token\": \"%s\"".formatted(tokenOf(invitation));

        accept(client, target, "false", "v1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));
        accept(client, target, "null", "v1").andExpect(status().isBadRequest());

        assertThat(activeLinks(client)).isZero();
        assertThat(consents(client)).isZero();
        assertThat(invitationStatus(invitation)).isEqualTo("PENDING");
    }

    @Test
    void termoDiferenteDoAtualDevolve409ENadaEGravado() throws Exception {
        String client = client();
        JsonNode invitation =
                invite(professional(ProfessionalType.PERSONAL_TRAINER, "Paula"), ProfessionalType.PERSONAL_TRAINER);

        accept(client, "\"token\": \"%s\"".formatted(tokenOf(invitation)), "true", "v0")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("O termo de consentimento mudou. Leia a versão atual antes de aceitar."));

        assertThat(activeLinks(client)).isZero();
        assertThat(invitationStatus(invitation)).isEqualTo("PENDING");
    }

    @Test
    void clienteSemPerfilNaoAceita() throws Exception {
        JsonNode invitation =
                invite(professional(ProfessionalType.PERSONAL_TRAINER, "Paula"), ProfessionalType.PERSONAL_TRAINER);

        acceptToken(UUID.randomUUID().toString(), invitation)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Crie seu perfil antes de aceitar um convite."));
    }

    @Test
    void comUmPersonalAtivoAceitarOutroDevolve409ComAOrientacao() throws Exception {
        String client = client();
        acceptToken(
                        client,
                        invite(
                                professional(ProfessionalType.PERSONAL_TRAINER, "Paula"),
                                ProfessionalType.PERSONAL_TRAINER))
                .andExpect(status().isCreated());
        JsonNode second =
                invite(professional(ProfessionalType.PERSONAL_TRAINER, "Pedro"), ProfessionalType.PERSONAL_TRAINER);

        acceptToken(client, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Você já tem um personal ativo. Encerre o vínculo atual antes de aceitar outro."));

        assertThat(activeLinks(client)).isEqualTo(1);
        // O convite recusado continua valendo para depois que o cliente encerrar o vínculo atual.
        assertThat(invitationStatus(second)).isEqualTo("PENDING");
    }

    @Test
    void mesmoProfissionalDeNovoDevolve409() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        acceptToken(client, invite(personal, ProfessionalType.PERSONAL_TRAINER)).andExpect(status().isCreated());

        acceptToken(client, invite(personal, ProfessionalType.PERSONAL_TRAINER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já está vinculado a este profissional."));
    }

    @Test
    void conviteUsadoVencidoOuCanceladoNaoViraVinculo() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        JsonNode used = invite(personal, ProfessionalType.PERSONAL_TRAINER);
        JsonNode expired = invite(personal, ProfessionalType.PERSONAL_TRAINER);
        JsonNode cancelled = invite(personal, ProfessionalType.PERSONAL_TRAINER);
        acceptToken(client(), used).andExpect(status().isCreated());
        jdbc.sql("""
                        update invitation
                           set created_at = timestamptz '2026-11-01T12:00:00Z',
                               expires_at = timestamptz '2026-11-08T12:00:00Z'
                         where id = :id
                        """).param("id", UUID.fromString(expired.get("id").asString())).update();
        mvc.perform(delete("/invitations/" + cancelled.get("id").asString())
                        .header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isNoContent());

        String other = client();
        acceptToken(other, used).andExpect(status().isGone());
        acceptToken(other, expired).andExpect(status().isGone());
        acceptToken(other, cancelled).andExpect(status().isGone());
        assertThat(activeLinks(other)).isZero();
    }

    @Test
    void profissionalNaoSeVinculaASiMesmo() throws Exception {
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        withProfile(personal);

        acceptToken(personal, invite(personal, ProfessionalType.PERSONAL_TRAINER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você não pode se vincular a si mesmo."));
        assertThat(activeLinks(personal)).isZero();
    }

    @Test
    void aceitesSimultaneosDeDoisPersonaisDeixamUmVinculoSo() throws Exception {
        String client = client();
        List<JsonNode> invitations = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            invitations.add(invite(
                    professional(ProfessionalType.PERSONAL_TRAINER, "Personal " + i),
                    ProfessionalType.PERSONAL_TRAINER));
        }
        List<Callable<Integer>> calls = new ArrayList<>();
        for (JsonNode invitation : invitations) {
            calls.add(() ->
                    acceptToken(client, invitation).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(calls.size())) {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(activeLinks(client)).isEqualTo(1);
        assertThat(consents(client)).isEqualTo(1);
    }

    @Test
    void aceitesSimultaneosDoMesmoConviteViramUmVinculoSo() throws Exception {
        JsonNode invitation =
                invite(professional(ProfessionalType.PERSONAL_TRAINER, "Paula"), ProfessionalType.PERSONAL_TRAINER);
        List<String> clients = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            clients.add(client());
        }
        List<Callable<Integer>> calls = new ArrayList<>();
        for (String client : clients) {
            calls.add(() ->
                    acceptToken(client, invitation).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(calls.size())) {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 410);
        assertThat(count(
                        "select count(*) from client_link where invitation_id = :id",
                        UUID.fromString(invitation.get("id").asString())))
                .isEqualTo(1);
    }

    @Test
    void bancoRecusaSegundoVinculoAtivoDoMesmoTipoConviteRepetidoEVinculoConsigoMesmo() throws Exception {
        String client = client();
        String personal = professional(ProfessionalType.PERSONAL_TRAINER, "Paula");
        String other = professional(ProfessionalType.PERSONAL_TRAINER, "Pedro");
        JsonNode invitation = invite(personal, ProfessionalType.PERSONAL_TRAINER);
        acceptToken(client, invitation).andExpect(status().isCreated());
        String insert = """
                insert into client_link (id, client_id, professional_id, professional_type, status, invitation_id,
                    started_at)
                values (:id, :client, :professional, 'PERSONAL_TRAINER', 'ACTIVE', :invitation, now())
                """;

        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("client", UUID.fromString(client))
                        .param("professional", UUID.fromString(other))
                        .param("invitation", null)
                        .update())
                .hasMessageContaining("ux_client_link_active_type");
        assertThatThrownBy(() -> jdbc.sql(insert.replace("'ACTIVE'", "'ENDED'"))
                        .param("id", UUID.randomUUID())
                        .param("client", UUID.fromString(client()))
                        .param("professional", UUID.fromString(personal))
                        .param(
                                "invitation",
                                UUID.fromString(invitation.get("id").asString()))
                        .update())
                .hasMessageContaining("uq_client_link_invitation");
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("client", UUID.fromString(other))
                        .param("professional", UUID.fromString(other))
                        .param("invitation", null)
                        .update())
                .hasMessageContaining("ck_client_link_not_self");
    }
}
