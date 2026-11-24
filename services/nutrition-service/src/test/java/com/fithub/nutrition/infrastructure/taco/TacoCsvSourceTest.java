package com.fithub.nutrition.infrastructure.taco;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.nutrition.application.FoodCatalogSource;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.InvalidFoodException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** O CSV versionado da TACO e a conversão pela legenda da planilha oficial (DEV-0075, ADR-011). */
class TacoCsvSourceTest {

    @Test
    void legendaDaPlanilhaTracoENaoAplicavelValemZero() {
        assertThat(TacoCsvSource.value("Tr")).isEqualByComparingTo("0");
        assertThat(TacoCsvSource.value("NA")).isEqualByComparingTo("0");
        assertThat(TacoCsvSource.value("*")).isNull();
        assertThat(TacoCsvSource.value("")).isNull();
        assertThat(TacoCsvSource.value(" 12.50 ")).isEqualByComparingTo("12.5");
        // Carboidrato por diferença levemente negativo (erro analítico) vale 0; o resto passa como está.
        assertThat(TacoCsvSource.carbsByDifference(new BigDecimal("-0.03"))).isEqualByComparingTo("0");
        assertThat(TacoCsvSource.carbsByDifference(new BigDecimal("28.06"))).isEqualByComparingTo("28.06");
        assertThat(TacoCsvSource.carbsByDifference(null)).isNull();
    }

    @Test
    void camposComVirgulaEntreAspas() {
        assertThat(TacoCsvSource.fields("1,\"Arroz, tipo 1, cozido\",Cereais,G,\"a \"\"b\"\"\""))
                .containsExactly("1", "Arroz, tipo 1, cozido", "Cereais", "G", "a \"b\"");
    }

    @Test
    void oCsvVersionadoTemAs597LinhasDaTacoESoAsSeisIncompletasFicamDeFora() {
        List<FoodCatalogSource.Entry> entries = new ArrayList<>();
        new TacoCsvSource(new ClassPathResource("taco/taco-4a-edicao.csv")).forEach(entries::add);

        assertThat(entries).hasSize(597);
        assertThat(new HashSet<>(entries.stream()
                        .map(FoodCatalogSource.Entry::externalId)
                        .toList()))
                .hasSize(597);
        List<String> invalid = new ArrayList<>();
        for (FoodCatalogSource.Entry entry : entries) {
            try {
                new Food(
                        UUID.randomUUID(),
                        Food.Kind.TACO,
                        null,
                        entry.name(),
                        entry.baseUnit(),
                        new Food.Nutrients(
                                entry.energyKcal(), entry.proteinG(), entry.carbsG(), entry.fatG(), entry.fiberG()),
                        entry.vegan(),
                        new Food.Source(TacoCsvSource.NAME, entry.externalId()),
                        Instant.now(),
                        Instant.now());
            } catch (InvalidFoodException rejected) {
                invalid.add(entry.externalId());
            }
        }
        // Leites e iogurte em reavaliação, aguardente e coco verde sem análise, e o item 540 sem descrição.
        assertThat(invalid).containsExactly("450", "457", "458", "472", "540", "591");
        FoodCatalogSource.Entry oliveOil = entries.stream()
                .filter(entry -> entry.externalId().equals("260"))
                .findFirst()
                .orElseThrow();
        assertThat(oliveOil.name()).isEqualTo("Azeite, de oliva, extra virgem");
        assertThat(oliveOil.energyKcal()).isEqualByComparingTo("884");
        assertThat(oliveOil.proteinG()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(oliveOil.fatG()).isEqualByComparingTo("100");
        assertThat(oliveOil.vegan()).isTrue();
    }
}
