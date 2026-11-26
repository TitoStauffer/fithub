package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.application.AccessLogRetention;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Auditoria da leitura das restrições de mobilidade pelo personal (DEV-0170, NFR-03, ADR-012): a leitura pelo personal
 * fica registrada, a do próprio aluno não; o expurgo tira o que passou de 6 meses; a exportação traz o registro.
 */
class TrainingAccessAuditTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccessLogRetention retention;

    @TestConfiguration
    static class Tokens {

        /** {@code personal-<sub>} traz o papel de personal; {@code conta-<sub>}, só o de cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("conta-", List.of("client"), "personal-", List.of("client", "personal"));
            return token -> rolesByPrefix.entrySet().stream()
                    .filter(entry -> token.startsWith(entry.getKey()))
                    .findFirst()
                    .map(entry -> Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject(token.substring(entry.getKey().length()))
                            .claim("realm_access", Map.of("roles", entry.getValue()))
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(300))
                            .build())
                    .orElseThrow(() -> new BadJwtException("token inválido"));
        }
    }

    private void link(UUID personal, UUID client) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :c, :p, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", personal)
                .update();
    }

    private List<String> resources(UUID client) {
        return jdbc.sql("select resource from professional_access_log where client_id = :c")
                .param("c", client)
                .query(String.class)
                .list();
    }

    @Test
    void leituraDasRestricoesPeloPersonalFicaRegistradaEADoAlunoNao() throws Exception {
        UUID personal = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(personal, client);

        mvc.perform(get("/clients/" + client + "/restrictions").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        mvc.perform(get("/me/restrictions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());

        assertThat(resources(client)).containsExactly("MOBILITY_RESTRICTIONS");
        assertThat(jdbc.sql("select professional_id from professional_access_log where client_id = :c")
                        .param("c", client)
                        .query(UUID.class)
                        .single())
                .isEqualTo(personal);
    }

    @Test
    void semVinculoNaoHaLeituraNemRegistro() throws Exception {
        UUID client = UUID.randomUUID();

        mvc.perform(get("/clients/" + client + "/restrictions")
                        .header("Authorization", "Bearer personal-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(resources(client)).isEmpty();
    }

    @Test
    void aExportacaoDoAlunoTrazORegistro() throws Exception {
        UUID personal = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(personal, client);
        mvc.perform(get("/clients/" + client + "/restrictions").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());
        // Uma leitura de outro aluno antes: só o filtro por aluno a deixa de fora (achado do PR #311).
        UUID other = UUID.randomUUID();
        link(personal, other);
        mvc.perform(get("/clients/" + other + "/restrictions").header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());

        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLog.length()").value(1))
                .andExpect(jsonPath("$.accessLog[0].resource").value("MOBILITY_RESTRICTIONS"))
                .andExpect(jsonPath("$.accessLog[0].professionalId").value(personal.toString()));
    }

    @Test
    void expurgoTiraSoOQuePassouDe6Meses() {
        Instant sixMonthsAgo =
                Instant.now().atZone(ZoneOffset.UTC).minusMonths(6).toInstant();
        UUID old = insert(sixMonthsAgo.minusSeconds(3600));
        UUID recent = insert(sixMonthsAgo.plusSeconds(3600));

        retention.purge();

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    void bancoRecusaRecursoForaDaLista() {
        assertThatThrownBy(() -> jdbc.sql("""
                                insert into professional_access_log (id, professional_id, client_id, resource,
                                                                     accessed_at)
                                values (:id, :p, :c, 'SESSIONS', now())
                                """)
                        .param("id", UUID.randomUUID())
                        .param("p", UUID.randomUUID())
                        .param("c", UUID.randomUUID())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into professional_access_log (id, professional_id, client_id, resource, accessed_at)
                        values (:id, :p, :c, 'MOBILITY_RESTRICTIONS', :at)
                        """)
                .param("id", id)
                .param("p", UUID.randomUUID())
                .param("c", UUID.randomUUID())
                .param("at", Timestamp.from(at))
                .update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from professional_access_log where id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    @Test
    void aFichaComOsAlertasDeRestricaoMostradaAoPersonalRegistraALeitura() throws Exception {
        UUID personal = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(personal, client);
        jdbc.sql("""
                        insert into mobility_restriction (id, client_id, region, description, recorded_by, created_at,
                                                          updated_at)
                        values (:id, :c, 'KNEE', 'dor no joelho', :p, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", personal)
                .update();
        UUID exercise = UUID.randomUUID();
        jdbc.sql("""
                        insert into exercise (id, kind, owner_id, name, equipment, created_at, updated_at)
                        values (:id, 'OWN', :owner, 'Agachamento auditado', 'BARBELL', now(), now())
                        """).param("id", exercise).param("owner", personal).update();
        jdbc.sql("insert into exercise_muscle_group (exercise_id, muscle_group) values (:id, 'GLUTES')")
                .param("id", exercise)
                .update();
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        insert into workout_plan (id, client_id, personal_id, status, author, created_at, updated_at)
                        values (:id, :c, :p, 'PUBLISHED', 'PERSONAL', now(), now())
                        """).param("id", plan).param("c", client).param("p", personal).update();
        jdbc.sql("""
                        insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks,
                                                          published_at, created_at)
                        values (:id, :plan, 1, 'Ficha A', date '2026-11-01', 8, now(), now())
                        """).param("id", version).param("plan", plan).update();
        jdbc.sql("insert into workout_division (version_id, position, letter) values (:v, 0, 'A')")
                .param("v", version)
                .update();
        jdbc.sql("""
                        insert into workout_item (version_id, division_position, position, exercise_id, sets, reps_min,
                                                  reps_max)
                        values (:v, 0, 0, :e, 3, 8, 12)
                        """).param("v", version).param("e", exercise).update();

        mvc.perform(get("/plans/" + plan).header("Authorization", "Bearer personal-" + personal))
                .andExpect(status().isOk());

        assertThat(resources(client)).containsExactly("MOBILITY_RESTRICTIONS");
    }
}
