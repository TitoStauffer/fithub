package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.nutrition.application.AccessLogRetention;
import com.fithub.testing.service.ServiceIntegrationTest;
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
 * Auditoria da leitura das restrições alimentares pela nutricionista (DEV-0171, NFR-03, ADR-012): a leitura pela nutricionista
 * fica registrada, a do próprio paciente não; o expurgo tira o que passou de 6 meses; a exportação traz o registro.
 */
class NutritionAccessAuditTest extends ServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccessLogRetention retention;

    @TestConfiguration
    static class Tokens {

        /** {@code nutri-<sub>} traz o papel de nutricionista; {@code conta-<sub>}, só o de cliente. */
        @Bean
        JwtDecoder jwtDecoder() {
            Map<String, List<String>> rolesByPrefix =
                    Map.of("conta-", List.of("client"), "nutri-", List.of("client", "nutritionist"));
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

    private void link(UUID nutritionist, UUID client) {
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:id, :c, :p, 'ACTIVE', now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("p", nutritionist)
                .update();
    }

    private List<String> resources(UUID client) {
        return jdbc.sql("select resource from professional_access_log where client_id = :c")
                .param("c", client)
                .query(String.class)
                .list();
    }

    @Test
    void leituraDasRestricoesPelaNutricionistaFicaRegistradaEADoPacienteNao() throws Exception {
        UUID nutritionist = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(nutritionist, client);

        mvc.perform(get("/clients/" + client + "/food-restrictions")
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk());
        mvc.perform(get("/me/food-restrictions").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk());

        assertThat(resources(client)).containsExactly("FOOD_RESTRICTIONS");
        assertThat(jdbc.sql("select professional_id from professional_access_log where client_id = :c")
                        .param("c", client)
                        .query(UUID.class)
                        .single())
                .isEqualTo(nutritionist);
    }

    @Test
    void semVinculoNaoHaLeituraNemRegistro() throws Exception {
        UUID client = UUID.randomUUID();

        mvc.perform(get("/clients/" + client + "/food-restrictions")
                        .header("Authorization", "Bearer nutri-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(resources(client)).isEmpty();
    }

    @Test
    void aExportacaoDoPacienteTrazORegistro() throws Exception {
        UUID nutritionist = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(nutritionist, client);
        mvc.perform(get("/clients/" + client + "/food-restrictions")
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk());
        // Uma leitura de outro paciente antes: só o filtro por paciente a deixa de fora.
        UUID other = UUID.randomUUID();
        link(nutritionist, other);
        mvc.perform(get("/clients/" + other + "/food-restrictions")
                        .header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk());

        mvc.perform(get("/me/export").header("Authorization", "Bearer conta-" + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLog.length()").value(1))
                .andExpect(jsonPath("$.accessLog[0].resource").value("FOOD_RESTRICTIONS"))
                .andExpect(jsonPath("$.accessLog[0].professionalId").value(nutritionist.toString()));
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
                                values (:id, :p, :c, 'MEAL_LOGS', now())
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
                        values (:id, :p, :c, 'FOOD_RESTRICTIONS', :at)
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
    void oPlanoComOAlertaDeRestricaoMostradoANutricionistaRegistraALeitura() throws Exception {
        UUID nutritionist = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        link(nutritionist, client);
        jdbc.sql("""
                        insert into food_restriction (id, client_id, kind, description, terms, recorded_by, created_at,
                                                      updated_at)
                        values (:id, :c, 'ALLERGY', 'amendoim', array['amendoim'], :n, now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("c", client)
                .param("n", nutritionist)
                .update();
        UUID food = UUID.randomUUID();
        jdbc.sql("""
                        insert into food (id, kind, owner_id, name, base_unit, energy_kcal, protein_g, carbs_g, fat_g,
                                          vegan, created_at, updated_at)
                        values (:id, 'OWN', :owner, 'Pasta de amendoim', 'G', 590, 25, 20, 50, true, now(), now())
                        """).param("id", food).param("owner", nutritionist).update();
        UUID plan = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)
                        values (:id, :c, :n, 'PUBLISHED', now(), now())
                        """)
                .param("id", plan)
                .param("c", client)
                .param("n", nutritionist)
                .update();
        jdbc.sql("""
                        insert into meal_plan_version (id, plan_id, number, name, starts_on, published_at, created_at)
                        values (:id, :plan, 1, 'Plano A', date '2026-11-01', now(), now())
                        """).param("id", version).param("plan", plan).update();
        jdbc.sql("insert into meal (version_id, position, name) values (:v, 0, 'Lanche')")
                .param("v", version)
                .update();
        jdbc.sql(
                        "insert into meal_item (version_id, meal_position, position, food_id, amount) values (:v, 0, 0, :f, 20)")
                .param("v", version)
                .param("f", food)
                .update();

        mvc.perform(get("/meal-plans/" + plan).header("Authorization", "Bearer nutri-" + nutritionist))
                .andExpect(status().isOk());

        assertThat(resources(client)).containsExactly("FOOD_RESTRICTIONS");
    }
}
