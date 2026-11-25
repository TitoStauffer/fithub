package com.fithub.nutrition.domain.restriction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras da restrição alimentar: tipo, descrição e termos (DEV-0080). */
class FoodRestrictionTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static FoodRestriction restriction(FoodRestriction.Kind kind, String description, List<String> terms) {
        return FoodRestriction.record(
                UUID.randomUUID(), UUID.randomUUID(), kind, description, terms, UUID.randomUUID(), NOW);
    }

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidRestrictionException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void descricaoETermosDentroDasFaixas() {
        FoodRestriction allergy = restriction(
                FoodRestriction.Kind.ALLERGY, "  Alergia à proteína do leite  ", List.of(" leite ", "queijo"));
        assertThat(allergy.description()).isEqualTo("Alergia à proteína do leite");
        assertThat(allergy.terms()).containsExactly("leite", "queijo");
        List<String> ten = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            ten.add("termo " + index);
        }
        assertThat(restriction(FoodRestriction.Kind.INTOLERANCE, "Lactose", ten).terms())
                .hasSize(10);

        assertInvalid("kind", () -> restriction(null, "Lactose", List.of("leite")));
        assertInvalid("description", () -> restriction(FoodRestriction.Kind.ALLERGY, "a", List.of("leite")));
        assertInvalid(
                "description", () -> restriction(FoodRestriction.Kind.ALLERGY, "a".repeat(201), List.of("leite")));
        assertInvalid("terms", () -> restriction(FoodRestriction.Kind.ALLERGY, "Lactose", List.of()));
        List<String> eleven = new ArrayList<>(ten);
        eleven.add("outro");
        assertInvalid("terms", () -> restriction(FoodRestriction.Kind.ALLERGY, "Lactose", eleven));
        assertInvalid("terms[1]", () -> restriction(FoodRestriction.Kind.ALLERGY, "Lactose", List.of("leite", "q")));
        assertInvalid("terms[0]", () -> restriction(FoodRestriction.Kind.ALLERGY, "Lactose", List.of("a".repeat(41))));
        // Termo repetido, sem diferenciar maiúsculas.
        assertInvalid(
                "terms[1]", () -> restriction(FoodRestriction.Kind.ALLERGY, "Lactose", List.of("Leite", "leite")));
    }

    @Test
    void alertaPeloTermoNoNomeSemDiferenciarMaiusculasNemAcentos() {
        FoodRestriction allergy =
                restriction(FoodRestriction.Kind.ALLERGY, "Alergia ao leite", List.of("LEITE", "lactose"));
        assertThat(allergy.alertsFor("Leite, de vaca, integral")).isTrue();
        assertThat(allergy.alertsFor("Iogurte com lactose")).isTrue();
        assertThat(allergy.alertsFor("Arroz, tipo 1, cozido")).isFalse();
        assertThat(allergy.alertsFor(null)).isFalse();
        FoodRestriction intolerance =
                restriction(FoodRestriction.Kind.INTOLERANCE, "Não tolera feijão", List.of("feijao"));
        assertThat(intolerance.alertsFor("Feijão, carioca, cozido")).isTrue();
        FoodRestriction accentedTerm =
                restriction(FoodRestriction.Kind.INTOLERANCE, "Não tolera feijão", List.of("FEIJÃO"));
        assertThat(accentedTerm.alertsFor("feijao preto")).isTrue();
        FoodRestriction preference =
                restriction(FoodRestriction.Kind.PREFERENCE, "Não gosta de arroz", List.of("arroz"));
        assertThat(preference.alertsFor("Arroz, tipo 1, cozido")).isFalse();
    }

    @Test
    void soAlergiaEIntoleranciaAlertam() {
        assertThat(FoodRestriction.Kind.ALLERGY.alerts()).isTrue();
        assertThat(FoodRestriction.Kind.INTOLERANCE.alerts()).isTrue();
        assertThat(FoodRestriction.Kind.PREFERENCE.alerts()).isFalse();
    }
}
