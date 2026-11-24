package com.fithub.nutrition.domain.restriction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Restrições alimentares e preferências dos pacientes (DEV-0080). */
public interface FoodRestrictionRepository {

    /** Grava a restrição nova ou a alteração, pelo id. */
    void save(FoodRestriction restriction);

    Optional<FoodRestriction> findById(UUID id);

    void delete(UUID id);

    /** As restrições do paciente, da mais antiga para a mais nova. */
    List<FoodRestriction> findByClient(UUID clientId);
}
