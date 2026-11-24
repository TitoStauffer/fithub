package com.fithub.training.domain.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras do exercício (DEV-0053): campos obrigatórios, limites, mídia só https e visibilidade (BR-13). */
class ExerciseTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    private static Exercise own(String name, Set<MuscleGroup> groups, String instructions, String mediaUrl) {
        return new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.OWN,
                OWNER,
                name,
                groups,
                Equipment.DUMBBELL,
                instructions,
                mediaUrl,
                null,
                NOW,
                NOW);
    }

    private static Exercise catalog(UUID ownerId) {
        return new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                ownerId,
                "Supino reto",
                Set.of(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
                Equipment.BARBELL,
                null,
                null,
                new Attribution("wger", "192", "CC-BY-SA 4.0", "wger.de contributors"),
                NOW,
                NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidExerciseException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void guardaOsCamposSemEspacosNasPontasETrocaTextoEmBrancoPorVazio() {
        Exercise exercise =
                own("  Rosca direta  ", Set.of(MuscleGroup.BICEPS), "   ", " https://videos.example/rosca ");

        assertThat(exercise.name()).isEqualTo("Rosca direta");
        assertThat(exercise.instructions()).isNull();
        assertThat(exercise.mediaUrl()).isEqualTo("https://videos.example/rosca");
    }

    @Test
    void nomePrecisaTerDe2A120Caracteres() {
        assertInvalid("name", () -> own("A", Set.of(MuscleGroup.BICEPS), null, null));
        assertInvalid("name", () -> own(null, Set.of(MuscleGroup.BICEPS), null, null));
        assertInvalid("name", () -> own("x".repeat(121), Set.of(MuscleGroup.BICEPS), null, null));
        assertThat(own("x".repeat(120), Set.of(MuscleGroup.BICEPS), null, null).name())
                .hasSize(120);
    }

    @Test
    void exigeAoMenosUmGrupoMuscular() {
        assertInvalid("muscleGroups", () -> own("Rosca direta", Set.of(), null, null));
        assertInvalid("muscleGroups", () -> own("Rosca direta", null, null, null));
    }

    @Test
    void instrucoesTemAte4000Caracteres() {
        assertInvalid("instructions", () -> own("Rosca direta", Set.of(MuscleGroup.BICEPS), "x".repeat(4001), null));
        assertThat(own("Rosca direta", Set.of(MuscleGroup.BICEPS), "x".repeat(4000), null)
                        .instructions())
                .hasSize(4000);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://videos.example/rosca",
                "HTTPS://videos.example/rosca",
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "https:///sem-host",
                "https://usuario:senha@videos.example/rosca",
                "videos.example/rosca",
                "https://videos.example/com espaco"
            })
    void midiaSoAceitaEnderecoHttpsComHost(String mediaUrl) {
        assertInvalid("mediaUrl", () -> own("Rosca direta", Set.of(MuscleGroup.BICEPS), null, mediaUrl));
    }

    @Test
    void midiaTemAte500Caracteres() {
        String base = "https://videos.example/";
        assertInvalid(
                "mediaUrl",
                () -> own("Rosca direta", Set.of(MuscleGroup.BICEPS), null, base + "x".repeat(501 - base.length())));
        assertThat(own("Rosca direta", Set.of(MuscleGroup.BICEPS), null, base + "x".repeat(500 - base.length()))
                        .mediaUrl())
                .hasSize(500);
    }

    @Test
    void exercicioProprioTemDonoEODoCatalogoNaoTem() {
        assertInvalid("ownerId", () -> catalog(OWNER));
        assertInvalid(
                "ownerId",
                () -> new Exercise(
                        UUID.randomUUID(),
                        Exercise.Kind.OWN,
                        null,
                        "Rosca direta",
                        Set.of(MuscleGroup.BICEPS),
                        Equipment.DUMBBELL,
                        null,
                        null,
                        null,
                        NOW,
                        NOW));
    }

    @Test
    void catalogoEVisivelParaTodosEOProprioSoParaODono() {
        Exercise own = own("Rosca direta", Set.of(MuscleGroup.BICEPS), null, null);

        assertThat(catalog(null).visibleTo(UUID.randomUUID())).isTrue();
        assertThat(catalog(null).visibleTo(null)).isTrue();
        assertThat(own.visibleTo(OWNER)).isTrue();
        assertThat(own.visibleTo(UUID.randomUUID())).isFalse();
        assertThat(own.visibleTo(null)).isFalse();
    }

    @Test
    void atribuicaoExigeFonteIdLicencaEAutor() {
        assertInvalid("attribution.license", () -> new Attribution("wger", "192", " ", "wger.de contributors"));
        assertInvalid("attribution.author", () -> new Attribution("wger", "192", "CC-BY-SA 4.0", null));
        assertInvalid("attribution.source", () -> new Attribution("", "192", "CC-BY-SA 4.0", "autor"));
        assertInvalid("attribution.externalId", () -> new Attribution("wger", null, "CC-BY-SA 4.0", "autor"));
    }

    private static Exercise imported(String mediaUrl, Attribution attribution) {
        return new Exercise(
                UUID.randomUUID(),
                Exercise.Kind.CATALOG,
                null,
                "Supino reto",
                Set.of(MuscleGroup.CHEST),
                Equipment.BARBELL,
                null,
                mediaUrl,
                attribution,
                NOW,
                NOW);
    }

    /** DEV-0054, ADR-010: a mídia importada só entra com o crédito dela, e o crédito só existe com a mídia. */
    @Test
    void midiaImportadaPrecisaDoCreditoDelaESoExisteComOLink() {
        Attribution textOnly = new Attribution("wger", "192", "CC-BY-SA 3", "wger.de");
        Attribution withMedia = new Attribution("wger", "192", "CC-BY-SA 3", "wger.de", "CC-BY-SA 4", "Everkinetic");

        assertInvalid("mediaUrl", () -> imported("https://wger.de/media/supino.png", textOnly));
        assertInvalid("mediaUrl", () -> imported(null, withMedia));
        assertThat(imported("https://wger.de/media/supino.png", withMedia)
                        .attribution()
                        .mediaAuthor())
                .isEqualTo("Everkinetic");
        assertThat(imported(null, textOnly).attribution().hasMediaCredit()).isFalse();
        assertInvalid(
                "attribution.mediaLicense",
                () -> new Attribution("wger", "192", "CC-BY-SA 3", "wger.de", "CC-BY-SA 4", " "));
        assertInvalid(
                "attribution.mediaLicense",
                () -> new Attribution("wger", "192", "CC-BY-SA 3", "wger.de", null, "autor"));
    }
}
