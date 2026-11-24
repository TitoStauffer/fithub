package com.fithub.training;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Busca de exercícios pelo personal (DEV-0055, FR-23) com Postgres real: nome sem maiúsculas nem acentos, filtros
 * combinados, visibilidade (BR-13), paginação e erros com o campo.
 */
class ExerciseSearchApiTest extends ServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    MockMvc mvc;

    /** Marca única nos nomes: o banco é compartilhado entre as classes de teste. */
    private String tag;

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
    void newTag() {
        tag = "zq" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
    }

    private Exercise save(String name, UUID owner, Set<MuscleGroup> groups, Equipment equipment) {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                owner == null ? Exercise.Kind.CATALOG : Exercise.Kind.OWN,
                owner,
                name,
                groups,
                equipment,
                null,
                null,
                null,
                NOW,
                NOW);
        exercises.save(exercise);
        return exercise;
    }

    private Exercise catalog(String name, MuscleGroup group, Equipment equipment) {
        return save(name, null, Set.of(group), equipment);
    }

    /** {@code query} em pares {@code campo=valor} separados por {@code &}; o valor vai sem codificar. */
    private static MockHttpServletRequestBuilder search(String account, String query) {
        MockHttpServletRequestBuilder request = get("/exercises").header("Authorization", "Bearer " + account);
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            request.param(pair.substring(0, equals), pair.substring(equals + 1));
        }
        return request;
    }

    private static String personal() {
        return "personal-" + UUID.randomUUID();
    }

    @Test
    void nomeIgnoraMaiusculasEAcentos() throws Exception {
        catalog("Extensão de Tríceps " + tag, MuscleGroup.TRICEPS, Equipment.CABLE);

        mvc.perform(search(personal(), "name=EXTENSAO DE TRICEPS " + tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Extensão de Tríceps " + tag));
        mvc.perform(search(personal(), "name=" + tag.toUpperCase()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void filtrosSeCombinamComONomeEOResultadoVemPorNome() throws Exception {
        catalog(tag + " Supino", MuscleGroup.CHEST, Equipment.BARBELL);
        catalog(tag + " Crucifixo", MuscleGroup.CHEST, Equipment.DUMBBELL);
        catalog(tag + " Remada", MuscleGroup.BACK, Equipment.BARBELL);

        mvc.perform(search(personal(), "name=" + tag + "&muscleGroup=CHEST"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].name").value(Matchers.contains(tag + " Crucifixo", tag + " Supino")));
        mvc.perform(search(personal(), "name=" + tag + "&muscleGroup=CHEST&equipment=BARBELL"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value(tag + " Supino"));
        mvc.perform(search(personal(), "name=" + tag + "&equipment=BARBELL"))
                .andExpect(jsonPath("$.items[*].name").value(Matchers.contains(tag + " Remada", tag + " Supino")));
        mvc.perform(search(personal(), "name=" + tag + "&muscleGroup=BACK&equipment=DUMBBELL"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void proprioDeOutroPersonalNuncaAparece() throws Exception {
        UUID me = UUID.randomUUID();
        catalog(tag + " Comum", MuscleGroup.CHEST, Equipment.BARBELL);
        save(tag + " Meu", me, Set.of(MuscleGroup.CHEST), Equipment.BARBELL);
        save(tag + " Dele", UUID.randomUUID(), Set.of(MuscleGroup.CHEST), Equipment.BARBELL);

        mvc.perform(search("personal-" + me, "name=" + tag))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].name").value(Matchers.contains(tag + " Comum", tag + " Meu")))
                .andExpect(jsonPath("$.items[1].kind").value("OWN"));
        // Conta com sub fora do formato UUID vê só o catálogo.
        mvc.perform(search("personal-nao-e-uuid", "name=" + tag))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value(tag + " Comum"));
    }

    @Test
    void paginaTrazOTotalEOTamanhoPadrao() throws Exception {
        for (String name : List.of("A", "B", "C")) {
            catalog(tag + " " + name, MuscleGroup.ABS, Equipment.BODYWEIGHT);
        }

        mvc.perform(search(personal(), "name=" + tag + "&size=2"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items[*].name").value(Matchers.contains(tag + " A", tag + " B")));
        mvc.perform(search(personal(), "name=" + tag + "&size=2&page=1"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].name").value(Matchers.contains(tag + " C")));
        mvc.perform(search(personal(), "name=" + tag))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(search(personal(), "name=" + tag + "&size=50")).andExpect(status().isOk());
    }

    @Test
    void curingaDoLikeNoNomeBuscadoEhTextoLiteral() throws Exception {
        catalog(tag + " 100% esforço", MuscleGroup.ABS, Equipment.BODYWEIGHT);

        // `_` e `%` não viram curinga: "<tag>_" não acha "<tag> " nem nada mais.
        mvc.perform(search(personal(), "name=" + tag + "_"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(search(personal(), "name=" + tag + "%"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(search(personal(), "name=100% esf"))
                .andExpect(jsonPath("$.total").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void filtroInvalidoRespondeBadRequestComOCampo() throws Exception {
        for (String[] query : new String[][] {
            {"muscleGroup=PESCOCO", "muscleGroup"},
            {"equipment=trampolim", "equipment"},
            {"page=-1", "page"},
            {"page=um", "page"},
            {"size=0", "size"},
            {"size=51", "size"},
            {"size=muitos", "size"},
            {"name=" + "a".repeat(121), "name"}
        }) {
            mvc.perform(search(personal(), query[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(query[1]));
        }
    }

    @Test
    void soOPapelDePersonalBusca() throws Exception {
        mvc.perform(search("conta-" + UUID.randomUUID(), "name=" + tag)).andExpect(status().isForbidden());
        mvc.perform(get("/exercises")).andExpect(status().isUnauthorized());
    }
}
