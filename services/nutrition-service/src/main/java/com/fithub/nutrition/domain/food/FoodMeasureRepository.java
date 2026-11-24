package com.fithub.nutrition.domain.food;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Medidas caseiras dos alimentos (DEV-0076). */
public interface FoodMeasureRepository {

    void save(FoodMeasure measure);

    Optional<FoodMeasure> findById(UUID id);

    void delete(UUID id);

    /** As medidas da nutricionista no alimento, pelo nome. */
    List<FoodMeasure> findFor(UUID foodId, UUID ownerId);
}
