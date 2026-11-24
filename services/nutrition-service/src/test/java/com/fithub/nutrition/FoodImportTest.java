package com.fithub.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.nutrition.application.ImportFoods;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.infrastructure.taco.TacoCsvSource;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Importação da TACO com Postgres real (DEV-0075): reexecutável, atualiza pelo id externo e pula o incompleto. */
class FoodImportTest extends ServiceIntegrationTest {

    @Autowired
    FoodRepository foods;

    @Autowired
    JdbcClient jdbc;

    private ImportFoods importing(String resource) {
        return new ImportFoods(foods, new TacoCsvSource(new ClassPathResource(resource)), Clock.systemUTC());
    }

    private int samples() {
        return jdbc.sql("select count(*) from food where external_source = :source and external_id like 'amostra-%'")
                .param("source", TacoCsvSource.NAME)
                .query(Integer.class)
                .single();
    }

    @Test
    void importarDeNovoNaoDuplicaAtualizaOQueMudouEPulaOIncompleto() {
        ImportFoods.Result first = importing("taco/amostra.csv").run();

        assertThat(first.created()).isEqualTo(4);
        assertThat(first.skippedIds()).containsExactly("amostra-4");
        assertThat(samples()).isEqualTo(4);
        // Carboidrato por diferença negativo na planilha entra como 0.
        assertThat(foods.findBySource(TacoCsvSource.NAME, "amostra-5")
                        .orElseThrow()
                        .per100()
                        .carbsG())
                .isEqualByComparingTo("0");
        Food biscuit = foods.findBySource(TacoCsvSource.NAME, "amostra-3").orElseThrow();
        // Traço vale 0; a fibra em reavaliação fica ausente.
        assertThat(biscuit.per100().fatG()).isEqualByComparingTo("0");
        assertThat(biscuit.per100().fiberG()).isNull();
        assertThat(biscuit.kind()).isEqualTo(Food.Kind.TACO);
        assertThat(biscuit.vegan()).isFalse();
        Food oil = foods.findBySource(TacoCsvSource.NAME, "amostra-2").orElseThrow();
        assertThat(oil.per100().proteinG()).isEqualByComparingTo("0");
        assertThat(oil.per100().fatG()).isEqualByComparingTo("100");

        ImportFoods.Result again = importing("taco/amostra.csv").run();
        assertThat(again.created()).isZero();
        assertThat(again.unchanged()).isEqualTo(4);
        assertThat(samples()).isEqualTo(4);

        Food rice = foods.findBySource(TacoCsvSource.NAME, "amostra-1").orElseThrow();
        ImportFoods.Result changed = importing("taco/amostra-atualizada.csv").run();
        assertThat(changed.updated()).isEqualTo(1);
        assertThat(changed.unchanged()).isEqualTo(3);
        Food updated = foods.findBySource(TacoCsvSource.NAME, "amostra-1").orElseThrow();
        assertThat(updated.id()).isEqualTo(rice.id());
        assertThat(updated.per100().proteinG()).isEqualByComparingTo("2.6");
        assertThat(samples()).isEqualTo(4);
    }
}
