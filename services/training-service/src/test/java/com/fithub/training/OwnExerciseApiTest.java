package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercícios próprios do personal (DEV-0056) com Postgres real: criar, editar e arquivar, só pelo dono (BR-13); o
 * arquivado continua legível por id e sai da busca.
 */
class OwnExerciseApiTest extends ServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    /** Marca única nos nomes: o banco é compartilhado entre as classes de teste. */
    private String tag;

    private UUID owner;

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

    @BeforeEach
    void setUp() {
        tag = "zq" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        owner = UUID.randomUUID();
    }

    private static String bearer(UUID account) {
        return "Bearer personal-" + account;
    }

    private String body(String name, String groups, String mediaUrl) {
        return """
                {"name": "%s", "muscleGroups": [%s], "equipment": "DUMBBELL",
                 "instructions": "Cotovelos colados ao corpo.", "mediaUrl": %s}
                """.formatted(name, groups, mediaUrl == null ? "null" : "\"" + mediaUrl + "\"");
    }

    private UUID create(UUID account, String name) throws Exception {
        String location = mvc.perform(post("/exercises")
                        .header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "\"BICEPS\"", null)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return UUID.fromString(location.substring("/exercises/".length()));
    }

    @Test
    void personalCriaEditaEArquivaOProprioExercicio() throws Exception {
        String created = mvc.perform(post("/exercises")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        // Um dono no corpo é ignorado: o dono vem sempre do token.
                        .content(body(tag + " Rosca", "\"BICEPS\", \"FOREARMS\"", "https://videos.example/rosca")
                                .replace("{", "{\"ownerId\": \"" + UUID.randomUUID() + "\", ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/exercises/")))
                .andExpect(jsonPath("$.kind").value("OWN"))
                .andExpect(jsonPath("$.name").value(tag + " Rosca"))
                .andExpect(jsonPath("$.muscleGroups[0]").value("BICEPS"))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
        assertThat(exercises.findById(id).orElseThrow().ownerId()).isEqualTo(owner);

        mvc.perform(put("/exercises/" + id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tag + " Rosca martelo", "\"BICEPS\"", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(tag + " Rosca martelo"))
                .andExpect(jsonPath("$.mediaUrl").isEmpty());
        mvc.perform(get("/exercises").param("name", tag).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(delete("/exercises/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        // Arquivado continua legível por id, para as fichas antigas, e sai da busca.
        mvc.perform(get("/exercises/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(tag + " Rosca martelo"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(get("/exercises").param("name", tag).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.total").value(0));
        Instant archivedAt = exercises.findById(id).orElseThrow().archivedAt();
        assertThat(archivedAt).isNotNull();

        // Arquivado não muda mais; arquivar de novo não troca a data.
        mvc.perform(put("/exercises/" + id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tag + " Outro nome", "\"BICEPS\"", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Exercício arquivado não pode ser editado."));
        mvc.perform(delete("/exercises/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        Exercise after = exercises.findById(id).orElseThrow();
        assertThat(after.archivedAt()).isEqualTo(archivedAt);
        assertThat(after.name()).isEqualTo(tag + " Rosca martelo");
    }

    @Test
    void criarEEditarUsamAsRegrasDoModeloEApontamOCampo() throws Exception {
        for (String[] invalid : new String[][] {
            {body("a", "\"BICEPS\"", null), "name"},
            {body(tag + " Rosca", "", null), "muscleGroups"},
            {body(tag + " Rosca", "\"BICEPS\"", "http://videos.example/rosca"), "mediaUrl"},
            {body(tag + " Rosca", "\"BICEPS\"", null).replace("\"DUMBBELL\"", "null"), "equipment"}
        }) {
            mvc.perform(post("/exercises")
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(invalid[1]));
        }
        UUID id = create(owner, tag + " Rosca");
        mvc.perform(put("/exercises/" + id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a", "\"BICEPS\"", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void exercicioDeOutroPersonalOuDoCatalogoRespondeComoInexistente() throws Exception {
        UUID theirs = create(UUID.randomUUID(), tag + " Dele");
        Exercise catalog = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                tag + " Comum",
                Set.of(MuscleGroup.CHEST),
                Equipment.BARBELL,
                null,
                null,
                null,
                NOW,
                NOW);
        exercises.save(catalog);

        for (UUID id : List.of(theirs, catalog.id(), UUID.randomUUID())) {
            mvc.perform(put("/exercises/" + id)
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(tag + " Tomado", "\"BICEPS\"", null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Exercício não encontrado."));
            mvc.perform(delete("/exercises/" + id).header("Authorization", bearer(owner)))
                    .andExpect(status().isNotFound());
        }
        assertThat(exercises.findById(theirs).orElseThrow().name()).isEqualTo(tag + " Dele");
        assertThat(exercises.findById(theirs).orElseThrow().archived()).isFalse();
        assertThat(exercises.findById(catalog.id()).orElseThrow()).isEqualTo(catalog);
        // Conta com sub fora do formato UUID não é dona de nada.
        mvc.perform(delete("/exercises/" + theirs).header("Authorization", "Bearer personal-nao-e-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soOPapelDePersonalAlteraExercicios() throws Exception {
        UUID id = create(owner, tag + " Rosca");

        mvc.perform(post("/exercises")
                        .header("Authorization", "Bearer conta-" + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tag + " Rosca", "\"BICEPS\"", null)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/exercises/" + id).header("Authorization", "Bearer conta-" + owner))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/exercises/" + id)).andExpect(status().isUnauthorized());
        assertThat(exercises.findById(id).orElseThrow().archived()).isFalse();
    }

    /** A CHECK da V6 recusa arquivar o catálogo sem passar pelo domínio. */
    @Test
    void bancoRecusaArquivarExercicioDoCatalogo() {
        assertThatThrownBy(() -> jdbc.sql("""
                                insert into exercise (id, kind, name, equipment, created_at, updated_at, archived_at)
                                values (:id, 'CATALOG', 'Supino', 'BARBELL', now(), now(), now())
                                """).param("id", UUID.randomUUID()).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_exercise_archived_own");
    }
}
