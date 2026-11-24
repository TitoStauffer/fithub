package com.fithub.nutrition.domain.food;

import java.util.Optional;
import java.util.UUID;

/** Alimentos da base da nutricionista (DEV-0074). */
public interface FoodRepository {

    /** Grava o alimento novo ou a alteração, pelo id. */
    void save(Food food);

    Optional<Food> findById(UUID id);
}
