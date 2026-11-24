package com.fithub.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * Leitura de exercício pelo personal (DEV-0053) com Postgres real: catálogo com atribuição, exercício próprio só
 * para o dono (BR-13) e as garantias do banco.
 */
class ExerciseApiTest extends ServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

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

    private Exercise catalog() {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                "Supino reto",
                Set.of(MuscleGroup.TRICEPS, MuscleGroup.CHEST),
                Equipment.BARBELL,
                "Deite no banco e desça a barra até o peito.",
                "https://videos.example/supino",
                new Attribution("wger", UUID.randomUUID().toString(), "CC-BY-SA 4.0", "wger.de contributors"),
                NOW,
                NOW);
        exercises.save(exercise);
        return exercise;
    }

    private Exercise ownOf(UUID owner) {
        Exercise exercise = new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.OWN,
                owner,
                "Rosca do meu jeito",
                Set.of(MuscleGroup.BICEPS),
                Equipment.DUMBBELL,
                null,
                null,
                null,
                NOW,
                NOW);
        exercises.save(exercise);
        return exercise;
    }

    @Test
    void personalLeOExercicioDoCatalogoComAAtribuicao() throws Exception {
        Exercise exercise = catalog();

        mvc.perform(get("/exercises/" + exercise.id()).header("Authorization", "Bearer personal-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(exercise.id().toString()))
                .andExpect(jsonPath("$.kind").value("CATALOG"))
                .andExpect(jsonPath("$.name").value("Supino reto"))
                .andExpect(jsonPath("$.muscleGroups[0]").value("CHEST"))
                .andExpect(jsonPath("$.muscleGroups[1]").value("TRICEPS"))
                .andExpect(jsonPath("$.equipment").value("BARBELL"))
                .andExpect(jsonPath("$.instructions").value("Deite no banco e desça a barra até o peito."))
                .andExpect(jsonPath("$.mediaUrl").value("https://videos.example/supino"))
                .andExpect(jsonPath("$.attribution.source").value("wger"))
                .andExpect(jsonPath("$.attribution.license").value("CC-BY-SA 4.0"))
                .andExpect(jsonPath("$.attribution.author").value("wger.de contributors"));
    }

    @Test
    void exercicioProprioSoApareceParaODono() throws Exception {
        UUID owner = UUID.randomUUID();
        Exercise exercise = ownOf(owner);

        mvc.perform(get("/exercises/" + exercise.id()).header("Authorization", "Bearer personal-" + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("OWN"))
                .andExpect(jsonPath("$.attribution").doesNotExist())
                // Ausente, não null: o contrato declara o campo opcional e sem null.
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).doesNotContain("attribution"));

        // Outro personal recebe o mesmo 404 de um id que não existe: não dá para saber que o exercício existe.
        mvc.perform(get("/exercises/" + exercise.id()).header("Authorization", "Bearer personal-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Exercício não encontrado."));
        mvc.perform(get("/exercises/" + UUID.randomUUID()).header("Authorization", "Bearer personal-" + owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Exercício não encontrado."));
    }

    @Test
    void contaComSubForaDoFormatoVeOCatalogoMasNenhumExercicioProprio() throws Exception {
        Exercise shared = catalog();
        Exercise own = ownOf(UUID.randomUUID());

        mvc.perform(get("/exercises/" + shared.id()).header("Authorization", "Bearer personal-nao-e-uuid"))
                .andExpect(status().isOk());
        mvc.perform(get("/exercises/" + own.id()).header("Authorization", "Bearer personal-nao-e-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soOPapelDePersonalLeExercicios() throws Exception {
        Exercise exercise = catalog();

        mvc.perform(get("/exercises/" + exercise.id()).header("Authorization", "Bearer conta-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/exercises/" + exercise.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void salvarDeNovoAtualizaOExercicioETrocaOsGruposMusculares() {
        Exercise exercise = catalog();

        exercises.save(new Exercise(
                exercise.id(),
                exercise.kind(),
                null,
                "Supino reto com barra",
                Set.of(MuscleGroup.CHEST),
                Equipment.BARBELL,
                null,
                null,
                exercise.attribution(),
                exercise.createdAt(),
                NOW.plusSeconds(60)));

        Exercise saved = exercises.findById(exercise.id()).orElseThrow();
        assertThat(saved.name()).isEqualTo("Supino reto com barra");
        assertThat(saved.muscleGroups()).containsExactly(MuscleGroup.CHEST);
        assertThat(saved.mediaUrl()).isNull();
        assertThat(saved.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void bancoRecusaDonoNoCatalogoMidiaSemHttpsEIdExternoRepetido() {
        Exercise first = catalog();
        String insert = """
                insert into exercise (id, kind, owner_id, name, equipment, media_url, external_source, external_id,
                    license, license_author, created_at, updated_at)
                values (:id, :kind, :owner, 'Supino', 'BARBELL', :media, :source, :externalId, :license, :author,
                    now(), now())
                """;

        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("kind", "CATALOG")
                        .param("owner", UUID.randomUUID())
                        .param("media", null)
                        .param("source", null)
                        .param("externalId", null)
                        .param("license", null)
                        .param("author", null)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_exercise_owner");
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("kind", "CATALOG")
                        .param("owner", null)
                        .param("media", "http://videos.example/supino")
                        .param("source", null)
                        .param("externalId", null)
                        .param("license", null)
                        .param("author", null)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_exercise_media_https");
        assertThatThrownBy(() -> jdbc.sql(insert)
                        .param("id", UUID.randomUUID())
                        .param("kind", "CATALOG")
                        .param("owner", null)
                        .param("media", null)
                        .param("source", "wger")
                        .param("externalId", first.attribution().externalId())
                        .param("license", "CC-BY-SA 4.0")
                        .param("author", "outro autor")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_exercise_external");
    }

    /** As outras CHECKs da V3 também recusam sem passar pelo domínio (aprendizado check-com-in-em-coluna-anulavel). */
    @Test
    void bancoRecusaTipoNomeEquipamentoAtribuicaoIncompletaEGrupoMuscularForaDaLista() {
        String insert = """
                insert into exercise (id, kind, owner_id, name, equipment, external_source, external_id, license,
                    license_author, created_at, updated_at)
                values (:id, :kind, null, :name, :equipment, :source, :externalId, :license, null, now(), now())
                """;
        record Row(String kind, String name, String equipment, String source, String constraint) {}
        for (Row row : new Row[] {
            new Row("OUTRO", "Supino", "BARBELL", null, "ck_exercise_kind"),
            new Row("CATALOG", " ", "BARBELL", null, "ck_exercise_name"),
            new Row("CATALOG", "Supino", "TRAMPOLIM", null, "ck_exercise_equipment"),
            new Row("CATALOG", "Supino", "BARBELL", "wger", "ck_exercise_attribution")
        }) {
            assertThatThrownBy(() -> jdbc.sql(insert)
                            .param("id", UUID.randomUUID())
                            .param("kind", row.kind())
                            .param("name", row.name())
                            .param("equipment", row.equipment())
                            .param("source", row.source())
                            .param(
                                    "externalId",
                                    row.source() == null
                                            ? null
                                            : UUID.randomUUID().toString())
                            .param("license", row.source() == null ? null : "CC-BY-SA 4.0")
                            .update())
                    .as(row.constraint())
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(row.constraint());
        }

        Exercise exercise = catalog();
        assertThatThrownBy(() -> jdbc.sql(
                                "insert into exercise_muscle_group (exercise_id, muscle_group) values (:id, 'PESCOCO')")
                        .param("id", exercise.id())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_exercise_muscle_group");
    }
}
