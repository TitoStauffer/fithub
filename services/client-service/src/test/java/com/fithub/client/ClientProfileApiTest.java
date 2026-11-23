package com.fithub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Perfil do cliente ponta a ponta (DEV-0032): HTTP, validação e Postgres real, com o relógio fixo. */
class ClientProfileApiTest extends ServiceIntegrationTest {

    private static final String BODY = """
            {"name": "  Ana Cliente ", "birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165,
             "goal": "HYPERTROPHY"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @TestConfiguration
    static class Fakes {

        /** Hoje, no teste, é 23/11/2026. */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        }

        /** Token de teste: {@code Bearer conta-<uuid>} vira um JWT com esse {@code sub}; o resto é recusado. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!token.startsWith("conta-")) {
                    throw new BadJwtException("token inválido");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring("conta-".length()))
                        .claim("realm_access", Map.of("roles", List.of("client")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
            };
        }
    }

    private static String newAccount() {
        return UUID.randomUUID().toString();
    }

    private ResultActions save(String account, String body) throws Exception {
        return mvc.perform(put("/clients/me/profile")
                .header("Authorization", "Bearer conta-" + account)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int profiles(String account) {
        return jdbc.sql("select count(*) from client_profile where account_id = :account")
                .param("account", account)
                .query(Integer.class)
                .single();
    }

    @Test
    void semTokenOPerfilERecusado() throws Exception {
        mvc.perform(put("/clients/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/clients/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void criaOPerfilComAIdadeCalculadaEDepoisLe() throws Exception {
        String account = newAccount();

        save(account, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Cliente"))
                .andExpect(jsonPath("$.birthDate").value("1990-11-24"))
                .andExpect(jsonPath("$.age").value(35))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.heightCm").value(165))
                .andExpect(jsonPath("$.goal").value("HYPERTROPHY"));

        mvc.perform(get("/clients/me/profile").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(35));
        // A idade não é gravada (FR-09).
        assertThat(jdbc.sql("select count(*) from information_schema.columns where table_name = 'client_profile'"
                                + " and column_name = 'age'")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void atualizaOProprioPerfilSemCriarOutro() throws Exception {
        String account = newAccount();
        save(account, BODY).andExpect(status().isCreated());

        save(account, """
                        {"name": "Ana Souza", "birthDate": "1990-11-24", "gender": "PREFER_NOT_TO_SAY", "heightCm": 166,
                         "goal": "HEALTH"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.gender").value("PREFER_NOT_TO_SAY"))
                .andExpect(jsonPath("$.goal").value("HEALTH"));

        assertThat(profiles(account)).isEqualTo(1);
    }

    @Test
    void cadaContaSoVeOProprioPerfil() throws Exception {
        String account = newAccount();
        save(account, BODY).andExpect(status().isCreated());

        mvc.perform(get("/clients/me/profile").header("Authorization", "Bearer conta-" + newAccount()))
                .andExpect(status().isNotFound());
    }

    @Test
    void contaDoPerfilVemDoTokenENuncaDoCorpo() throws Exception {
        String account = newAccount();
        String other = newAccount();

        save(account, BODY.replace("\"goal\"", "\"accountId\": \"%s\", \"goal\"".formatted(other)))
                .andExpect(status().isCreated());

        assertThat(profiles(account)).isEqualTo(1);
        assertThat(profiles(other)).isZero();
    }

    @Test
    void menorDeIdadeERecusadoComOCampo() throws Exception {
        String account = newAccount();

        save(account, BODY.replace("1990-11-24", "2008-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
        assertThat(profiles(account)).isZero();
    }

    @Test
    void camposInvalidosVoltamComOCampoEmProblemDetails() throws Exception {
        String account = newAccount();

        save(account, BODY.replace("165", "300"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("heightCm"));
        save(account, BODY.replace("\"FEMALE\"", "\"INVALIDO\"")).andExpect(status().isBadRequest());
        save(account, """
                        {"birthDate": "1990-11-24", "gender": "FEMALE", "heightCm": 165, "goal": "HEALTH"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        save(account, BODY.replace("1990-11-24", "2027-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));

        assertThat(profiles(account)).isZero();
    }

    @Test
    void pedidosSimultaneosDaMesmaContaTerminamComUmPerfil() throws Exception {
        String account = newAccount();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> save(account, BODY).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                statuses.add(result.get());
            }
        }

        // Um pedido cria (201) e os outros atualizam (200); o banco decide qual.
        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(profiles(account)).isEqualTo(1);
    }
}
