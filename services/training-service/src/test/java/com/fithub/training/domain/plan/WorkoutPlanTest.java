package com.fithub.training.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras da ficha de treino (DEV-0059): limites, letras das divisões, rascunho, publicação e substituição. */
class WorkoutPlanTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 11, 24);
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID PERSONAL = UUID.randomUUID();

    private static List<PlanVersion.DivisionDraft> named(List<String> names) {
        return names.stream().map(PlanVersion.DivisionDraft::named).toList();
    }

    /** Divisão com um exercício: publicar exige ao menos um em cada (BR-10). */
    private static PlanVersion.DivisionDraft withExercise(String name) {
        return new PlanVersion.DivisionDraft(
                name, List.of(new PlanItem.Draft(UUID.randomUUID(), 3, 10, null, null, null, null, null, null)));
    }

    private static WorkoutPlan draft(List<String> divisions) {
        return WorkoutPlan.newDraft(CLIENT, PERSONAL, "Hipertrofia", START, 8, named(divisions), NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidPlanException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void divisoesRecebemALetraDaPosicaoEONomeOpcional() {
        WorkoutPlan plan = draft(Arrays.asList("Peito", null, "  ", " Pernas "));

        assertThat(plan.current().divisions())
                .containsExactly(
                        new PlanVersion.Division("A", "Peito"),
                        new PlanVersion.Division("B", null),
                        new PlanVersion.Division("C", null),
                        new PlanVersion.Division("D", "Pernas"));
        assertThat(plan.status()).isEqualTo(WorkoutPlan.Status.DRAFT);
        assertThat(plan.current().number()).isEqualTo(1);
        assertThat(plan.expiresOn()).isEqualTo(START.plusWeeks(8));
    }

    @Test
    void limitesDoNomeDaValidadeEDasDivisoes() {
        List<PlanVersion.DivisionDraft> one = named(List.of("A"));
        assertInvalid("name", () -> WorkoutPlan.newDraft(CLIENT, PERSONAL, "a", START, 8, one, NOW));
        assertInvalid("name", () -> WorkoutPlan.newDraft(CLIENT, PERSONAL, "a".repeat(81), START, 8, one, NOW));
        assertInvalid("startsOn", () -> WorkoutPlan.newDraft(CLIENT, PERSONAL, "Ficha", null, 8, one, NOW));
        assertInvalid("validityWeeks", () -> WorkoutPlan.newDraft(CLIENT, PERSONAL, "Ficha", START, 0, one, NOW));
        assertInvalid("validityWeeks", () -> WorkoutPlan.newDraft(CLIENT, PERSONAL, "Ficha", START, 53, one, NOW));
        assertInvalid("divisions", () -> draft(List.of()));
        assertInvalid("divisions", () -> draft(List.of("A", "B", "C", "D", "E", "F", "G", "H")));
        assertInvalid("divisions[1].name", () -> draft(List.of("A", "x".repeat(61))));
        assertThat(draft(List.of("A", "B", "C", "D", "E", "F", "G")).current().divisions())
                .hasSize(7);
        assertThat(WorkoutPlan.newDraft(CLIENT, PERSONAL, "Ficha", START, 52, one, NOW)
                        .expiresOn())
                .isEqualTo(START.plusWeeks(52));
    }

    @Test
    void rascunhoEditaNaMesmaVersaoEPublicadaNaoEditaNemPublicaDeNovo() {
        WorkoutPlan plan = draft(List.of("A", "B"));
        Instant later = NOW.plusSeconds(60);

        WorkoutPlan edited = plan.editDraft("Força", START.plusDays(1), 4, List.of(withExercise("X")), later);
        assertThat(edited.current().id()).isEqualTo(plan.current().id());
        assertThat(edited.current().name()).isEqualTo("Força");
        assertThat(edited.updatedAt()).isEqualTo(later);

        WorkoutPlan published = edited.publish(later);
        assertThat(published.status()).isEqualTo(WorkoutPlan.Status.PUBLISHED);
        assertThat(published.current().publishedAt()).isEqualTo(later);
        assertThatThrownBy(() -> published.editDraft("Outra", START, 4, named(List.of("A")), later))
                .isInstanceOf(PlanStateException.class);
        assertThatThrownBy(() -> published.publish(later)).isInstanceOf(PlanStateException.class);

        WorkoutPlan replaced = published.replace(later);
        assertThat(replaced.status()).isEqualTo(WorkoutPlan.Status.REPLACED);
        assertThatThrownBy(() -> replaced.publish(later)).isInstanceOf(PlanStateException.class);
        assertThatThrownBy(() -> plan.replace(later)).isInstanceOf(PlanStateException.class);
    }

    @Test
    void soOAutorEDonoDaFicha() {
        WorkoutPlan plan = draft(List.of("A"));

        assertThat(plan.authoredBy(PERSONAL)).isTrue();
        assertThat(plan.authoredBy(UUID.randomUUID())).isFalse();
        assertThat(plan.authoredBy(null)).isFalse();
    }

    @Test
    void publicarExigeAoMenosUmExercicioEmCadaDivisao() {
        WorkoutPlan plan = WorkoutPlan.newDraft(
                CLIENT,
                PERSONAL,
                "Hipertrofia",
                START,
                8,
                List.of(withExercise("A"), PlanVersion.DivisionDraft.named("B")),
                NOW);

        assertInvalid("divisions[1].items", () -> plan.publish(NOW));
        assertThat(plan.editDraft("Hipertrofia", START, 8, List.of(withExercise("A"), withExercise("B")), NOW)
                        .publish(NOW)
                        .status())
                .isEqualTo(WorkoutPlan.Status.PUBLISHED);
    }
}
