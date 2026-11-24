package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.food.Food;
import java.math.BigDecimal;
import java.util.function.Consumer;

/** Base externa de alimentos, como a Tabela TACO (DEV-0075, ADR-011). */
public interface FoodCatalogSource {

    /** Nome da fonte, gravado em cada alimento importado (com o id externo, a chave da reimportação). */
    String name();

    /** Entrega cada item da base, na ordem dela. */
    void forEach(Consumer<Entry> consumer);

    /**
     * Um item da base, com os valores por 100 já convertidos; ausente quando a base não traz (o domínio decide se
     * o item pode entrar).
     */
    record Entry(
            String externalId,
            String name,
            Food.BaseUnit baseUnit,
            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatG,
            BigDecimal fiberG,
            boolean vegan) {}
}
