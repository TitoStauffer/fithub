package com.fithub.nutrition.domain.log;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Registros de refeição do paciente (DEV-0087). */
public interface MealLogRepository {

    /** Grava o registro novo; a refeição já registrada no dia bate na unicidade do banco. */
    void add(MealLog log);

    /** Grava a correção do estado e das alternativas. */
    void update(MealLog log);

    Optional<MealLog> findById(UUID id);

    /** Os registros do paciente no plano, num dia, pela ordem das refeições. */
    List<MealLog> findByPlanAndDay(UUID clientId, UUID planId, LocalDate day);

    void delete(UUID id);
}
