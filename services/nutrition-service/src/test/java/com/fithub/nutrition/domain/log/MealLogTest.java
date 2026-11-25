package com.fithub.nutrition.domain.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fithub.nutrition.domain.plan.Meal;
import com.fithub.nutrition.domain.plan.MealItem;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do registro de refeição (DEV-0087): a refeição, as alternativas, o estado e o prazo do dia. */
class MealLogTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");

    /** Café com arroz (2 alternativas) e feijão (sem alternativa); almoço com arroz. */
    private static MealPlanVersion version() {
        MealItem rice = new MealItem(
                UUID.randomUUID(),
                BigDecimal.TEN,
                null,
                null,
                List.of(
                        new MealItem.Alternative(UUID.randomUUID(), BigDecimal.TEN),
                        new MealItem.Alternative(UUID.randomUUID(), BigDecimal.ONE)));
        MealItem beans = new MealItem(UUID.randomUUID(), BigDecimal.TEN, null, null);
        return new MealPlanVersion(
                UUID.randomUUID(),
                1,
                "Plano",
                TODAY,
                null,
                List.of(
                        new Meal(0, "Café", null, null, List.of(rice, beans)),
                        new Meal(1, "Almoço", null, null, List.of(rice))),
                NOW,
                NOW);
    }

    private static MealLog log(Integer meal, MealLog.Status status, List<MealLog.Substitution> substitutions) {
        return MealLog.record(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                version(),
                meal,
                status,
                substitutions,
                null,
                TODAY,
                NOW);
    }

    private static MealLog.Substitution use(Integer item, Integer alternative) {
        return new MealLog.Substitution(item, alternative);
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOf(InvalidMealLogException.class)
                .extracting(error -> ((InvalidMealLogException) error).field())
                .isEqualTo(field);
    }

    @Test
    void registraARefeicaoComAsAlternativasUsadasNaVersao() {
        MealPlanVersion version = version();
        MealLog log = MealLog.record(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                version,
                0,
                MealLog.Status.PARTIAL,
                List.of(use(0, 1)),
                null,
                TODAY,
                NOW);
        assertThat(log.versionId()).isEqualTo(version.id());
        assertThat(log.versionNumber()).isEqualTo(1);
        assertThat(log.loggedOn()).isEqualTo(TODAY);
        assertThat(log.substitutions()).containsExactly(use(0, 1));
        assertThat(log(1, MealLog.Status.SKIPPED, null).substitutions()).isEmpty();
        assertThat(log(1, MealLog.Status.DONE, List.of(use(0, 0))).mealPosition())
                .isEqualTo(1);
    }

    @Test
    void refeicaoEstadoEAlternativasForaDasRegras() {
        assertField(() -> log(0, null, List.of()), "status");
        assertField(() -> log(null, MealLog.Status.DONE, List.of()), "mealPosition");
        assertField(() -> log(-1, MealLog.Status.DONE, List.of()), "mealPosition");
        assertField(() -> log(2, MealLog.Status.DONE, List.of()), "mealPosition");
        assertField(() -> log(0, MealLog.Status.SKIPPED, List.of(use(0, 0))), "substitutions");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(2, 0))), "substitutions[0].itemPosition");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(-1, 0))), "substitutions[0].itemPosition");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(null, 0))), "substitutions[0].itemPosition");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(0, 0), use(0, 1))), "substitutions[1].itemPosition");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(0, 2))), "substitutions[0].alternative");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(0, -1))), "substitutions[0].alternative");
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(0, null))), "substitutions[0].alternative");
        // O feijão não tem alternativa.
        assertField(() -> log(0, MealLog.Status.DONE, List.of(use(1, 0))), "substitutions[0].alternative");
        List<MealLog.Substitution> withNull = new ArrayList<>();
        withNull.add(null);
        assertField(() -> log(0, MealLog.Status.DONE, withNull), "substitutions[0]");
    }

    @Test
    void corrigeSoNoMesmoDiaENaMesmaRefeicao() {
        MealPlanVersion version = version();
        MealLog log = MealLog.record(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                version,
                0,
                MealLog.Status.DONE,
                List.of(use(0, 0)),
                null,
                TODAY,
                NOW);
        Instant later = NOW.plusSeconds(60);
        MealLog corrected = log.correct(version, 0, MealLog.Status.SKIPPED, List.of(), null, TODAY, later);
        assertThat(corrected.status()).isEqualTo(MealLog.Status.SKIPPED);
        assertThat(corrected.substitutions()).isEmpty();
        assertThat(corrected.createdAt()).isEqualTo(NOW);
        assertThat(corrected.updatedAt()).isEqualTo(later);
        assertThat(corrected.loggedOn()).isEqualTo(TODAY);

        assertThatThrownBy(
                        () -> log.correct(version, 0, MealLog.Status.DONE, List.of(), null, TODAY.plusDays(1), later))
                .isInstanceOf(MealLogConflictException.class);
        assertThatThrownBy(() -> log.correct(version, 1, MealLog.Status.DONE, List.of(), null, TODAY, later))
                .isInstanceOf(MealLogConflictException.class);
        assertThatThrownBy(() -> log.correct(version, null, MealLog.Status.DONE, List.of(), null, TODAY, later))
                .isInstanceOf(MealLogConflictException.class);
        assertField(
                () -> log.correct(version, 0, MealLog.Status.DONE, List.of(use(0, 2)), null, TODAY, later),
                "substitutions[0].alternative");
        assertThatThrownBy(() -> log.requireCorrectableOn(TODAY.plusDays(1)))
                .isInstanceOf(MealLogConflictException.class);
    }

    private static MealLog withDeviation(String deviation) {
        return MealLog.record(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                version(),
                0,
                MealLog.Status.SKIPPED,
                List.of(),
                deviation,
                TODAY,
                NOW);
    }

    @Test
    void desvioAte500SemEspacosNasPontas() {
        assertThat(withDeviation("  Comi pizza no lugar  ").deviation()).isEqualTo("Comi pizza no lugar");
        assertThat(withDeviation("   ").deviation()).isNull();
        assertThat(withDeviation(null).deviation()).isNull();
        assertThat(withDeviation("a".repeat(500)).deviation()).hasSize(500);
        assertField(() -> withDeviation("a".repeat(501)), "deviation");
        MealLog log = withDeviation("Pulei");
        MealPlanVersion version = version();
        assertThat(log.correct(version, 0, MealLog.Status.DONE, List.of(), "Comi tudo", TODAY, NOW)
                        .deviation())
                .isEqualTo("Comi tudo");
        assertField(
                () -> log.correct(version, 0, MealLog.Status.DONE, List.of(), "a".repeat(501), TODAY, NOW),
                "deviation");
    }

    @Test
    void fotoSoNoDiaDoRegistroEACorrecaoNaoATira() {
        MealLog log = withDeviation(null);
        MealLog.Photo photo = new MealLog.Photo("meal-logs/x/y/photo-1", NOW);
        MealLog withPhoto = log.withPhoto(photo, TODAY, NOW);
        assertThat(withPhoto.photo()).isEqualTo(photo);
        assertThat(withPhoto
                        .correct(version(), 0, MealLog.Status.DONE, List.of(), null, TODAY, NOW)
                        .photo())
                .isEqualTo(photo);
        assertThat(withPhoto.withoutPhoto(TODAY, NOW).photo()).isNull();
        assertThatThrownBy(() -> log.withPhoto(photo, TODAY.plusDays(1), NOW))
                .isInstanceOf(MealLogConflictException.class);
        assertThatThrownBy(() -> withPhoto.withoutPhoto(TODAY.plusDays(1), NOW))
                .isInstanceOf(MealLogConflictException.class);
    }

    @Test
    void fotoSoJpegOuPng() {
        assertThat(MealLog.requirePhotoContentType("image/jpeg")).isEqualTo("image/jpeg");
        assertThat(MealLog.requirePhotoContentType("image/png")).isEqualTo("image/png");
        assertField(() -> MealLog.requirePhotoContentType("image/gif"), "contentType");
        assertField(() -> MealLog.requirePhotoContentType(null), "contentType");
    }
}
