package com.fithub.nutrition.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fithub.nutrition.domain.food.Food;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras do plano alimentar (DEV-0081): faixas de cada campo, o BR-10 e os estados. */
class MealPlanTest {

    private static final UUID FOOD = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static MealItem item(String amount) {
        return new MealItem(FOOD, new BigDecimal(amount), null, null);
    }

    private static Meal.Draft meal(String name, MealItem... items) {
        return new Meal.Draft(name, LocalTime.of(7, 30), null, List.of(items));
    }

    private static MealPlan draft(List<Meal.Draft> meals) {
        return MealPlan.newDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Plano de novembro",
                LocalDate.of(2026, 11, 25),
                null,
                meals,
                NOW);
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOf(InvalidMealPlanException.class)
                .extracting(error -> ((InvalidMealPlanException) error).field())
                .isEqualTo(field);
    }

    @Test
    void quantidadeDoItemVaiDe0v1A5000() {
        assertThat(item("0.1").amount()).isEqualByComparingTo("0.1");
        assertThat(item("5000").amount()).isEqualByComparingTo("5000");
        assertField(() -> item("0.09"), "amount");
        assertField(() -> item("5000.1"), "amount");
        assertField(() -> new MealItem(FOOD, null, null, null), "amount");
        assertField(() -> new MealItem(null, BigDecimal.TEN, null, null), "foodId");
    }

    @Test
    void medidaVemComAQuantidadeDeMedidasDe0v1A100() {
        UUID measure = UUID.randomUUID();
        assertThat(new MealItem(FOOD, BigDecimal.TEN, measure, new BigDecimal("0.1")).measureCount())
                .isEqualByComparingTo("0.1");
        assertThat(new MealItem(FOOD, BigDecimal.TEN, measure, new BigDecimal("100")).measureCount())
                .isEqualByComparingTo("100");
        assertField(() -> new MealItem(FOOD, BigDecimal.TEN, measure, new BigDecimal("0.09")), "measureCount");
        assertField(() -> new MealItem(FOOD, BigDecimal.TEN, measure, new BigDecimal("100.1")), "measureCount");
        assertField(() -> new MealItem(FOOD, BigDecimal.TEN, measure, null), "measureCount");
        assertField(() -> new MealItem(FOOD, BigDecimal.TEN, null, BigDecimal.ONE), "measureCount");
    }

    @Test
    void refeicaoTemNomeDe2A60ObservacaoAte500EAte30Itens() {
        assertThat(new Meal(0, "Ca", null, null, List.of()).name()).isEqualTo("Ca");
        assertThat(new Meal(0, "a".repeat(60), null, "o".repeat(500), List.of()).note())
                .hasSize(500);
        assertThat(new Meal(0, "Café", null, null, Collections.nCopies(30, item("10"))).items())
                .hasSize(30);
        assertField(() -> new Meal(0, " C ", null, null, List.of()), "name");
        assertField(() -> new Meal(0, "a".repeat(61), null, null, List.of()), "name");
        assertField(() -> new Meal(0, "Café", null, "o".repeat(501), List.of()), "note");
        assertField(() -> new Meal(0, "Café", null, null, Collections.nCopies(31, item("10"))), "items");
    }

    @Test
    void planoTemNomeDe2A80InicioObservacaoAte500EDe1A10Refeicoes() {
        UUID id = UUID.randomUUID();
        List<Meal> one = List.of(new Meal(0, "Café", null, null, List.of()));
        assertThat(new MealPlanVersion(id, 1, "ab", LocalDate.now(), null, one, null, NOW).name())
                .isEqualTo("ab");
        assertThat(new MealPlanVersion(id, 1, "a".repeat(80), LocalDate.now(), "o".repeat(500), one, null, NOW).note())
                .hasSize(500);
        assertField(() -> new MealPlanVersion(id, 1, "a", LocalDate.now(), null, one, null, NOW), "name");
        assertField(() -> new MealPlanVersion(id, 1, "a".repeat(81), LocalDate.now(), null, one, null, NOW), "name");
        assertField(() -> new MealPlanVersion(id, 1, "Plano", null, null, one, null, NOW), "startsOn");
        assertField(
                () -> new MealPlanVersion(id, 1, "Plano", LocalDate.now(), "o".repeat(501), one, null, NOW), "note");
        assertField(() -> new MealPlanVersion(id, 1, "Plano", LocalDate.now(), null, List.of(), null, NOW), "meals");

        List<Meal.Draft> ten = new ArrayList<>(Collections.nCopies(10, meal("Lanche", item("10"))));
        assertThat(draft(ten).current().meals()).hasSize(10);
        ten.add(meal("Ceia", item("10")));
        assertField(() -> draft(ten), "meals");
        assertField(() -> draft(List.of()), "meals");
    }

    @Test
    void erroDaRefeicaoApontaOCaminho() {
        assertField(() -> draft(List.of(meal("Café", item("10")), meal("x", item("10")))), "meals[1].name");
        List<Meal.Draft> withNull = new ArrayList<>();
        withNull.add(null);
        assertField(() -> draft(withNull), "meals[0]");
    }

    @Test
    void publicarExigeAlimentoEmCadaRefeicao() {
        MealPlan plan = draft(List.of(meal("Café", item("100")), meal("Almoço")));
        assertField(() -> plan.publish(NOW), "meals[1].items");

        MealPlan published = draft(List.of(meal("Café", item("100")))).publish(NOW);
        assertThat(published.status()).isEqualTo(MealPlan.Status.PUBLISHED);
        assertThat(published.current().publishedAt()).isEqualTo(NOW);
    }

    @Test
    void soORascunhoEditaEPublicaESoOPublicadoESubstituido() {
        MealPlan plan = draft(List.of(meal("Café", item("100"))));
        assertThatThrownBy(() -> plan.replace(NOW)).isInstanceOf(MealPlanStateException.class);
        MealPlan published = plan.publish(NOW);
        assertThatThrownBy(() -> published.publish(NOW)).isInstanceOf(MealPlanStateException.class);
        assertThatThrownBy(() ->
                        published.editDraft("Outro", LocalDate.now(), null, List.of(meal("Café", item("10"))), NOW))
                .isInstanceOf(MealPlanStateException.class);
        assertThat(published.replace(NOW).status()).isEqualTo(MealPlan.Status.REPLACED);

        MealPlan edited = plan.editDraft("Outro nome", LocalDate.now(), null, List.of(meal("Ceia", item("20"))), NOW);
        assertThat(edited.current().name()).isEqualTo("Outro nome");
        assertThat(edited.current().id()).isEqualTo(plan.current().id());
    }

    @Test
    void somaDosMacrosPerdeAFibraQuandoFaltaEmAlguma() {
        Food.Amounts rice = new Food.Amounts(
                new BigDecimal("128.0"),
                new BigDecimal("2.5"),
                new BigDecimal("28.1"),
                new BigDecimal("0.2"),
                new BigDecimal("1.6"));
        Food.Amounts beans = new Food.Amounts(
                new BigDecimal("38.0"), new BigDecimal("2.4"), new BigDecimal("6.8"), new BigDecimal("0.3"), null);
        Food.Amounts withFiber = Food.Amounts.ZERO.plus(rice).plus(rice);
        assertThat(withFiber.proteinG()).isEqualByComparingTo("5.0");
        assertThat(withFiber.fiberG()).isEqualByComparingTo("3.2");
        Food.Amounts sum = rice.plus(beans);
        assertThat(sum.energyKcal()).isEqualByComparingTo("166.0");
        assertThat(sum.proteinG()).isEqualByComparingTo("4.9");
        assertThat(sum.carbsG()).isEqualByComparingTo("34.9");
        assertThat(sum.fatG()).isEqualByComparingTo("0.5");
        assertThat(sum.fiberG()).isNull();
    }
}
