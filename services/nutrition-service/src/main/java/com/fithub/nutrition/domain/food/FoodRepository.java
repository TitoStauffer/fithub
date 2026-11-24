package com.fithub.nutrition.domain.food;

import java.util.Optional;
import java.util.UUID;

/** Alimentos da base da nutricionista (DEV-0074). */
public interface FoodRepository {

    /** Grava o alimento novo ou a alteração, pelo id. */
    void save(Food food);

    Optional<Food> findById(UUID id);

    /** Busca na TACO e nos próprios da nutricionista, sem os arquivados (DEV-0078, BR-13). */
    FoodSearch.Page search(FoodSearch search, UUID nutritionistId);

    /** Se o alimento é ingrediente de alguma receita (DEV-0077). */
    boolean usedAsIngredient(UUID foodId);

    /** O alimento importado pela fonte e pelo id nela (DEV-0075): a chave da reimportação. */
    Optional<Food> findBySource(String source, String externalId);
}
