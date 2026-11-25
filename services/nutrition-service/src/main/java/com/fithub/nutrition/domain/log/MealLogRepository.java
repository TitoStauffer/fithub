package com.fithub.nutrition.domain.log;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Registros de refeição do paciente (DEV-0087). */
public interface MealLogRepository {

    /** Grava o registro novo; a refeição já registrada no dia bate na unicidade do banco. */
    void add(MealLog log);

    /** Grava a correção do estado, das alternativas e do desvio; a foto tem gravação própria. */
    void update(MealLog log);

    /** Grava só a foto do registro (ou a falta dela): a correção que chega junto não a desfaz. */
    void savePhoto(MealLog log);

    /** Se a chave é da foto confirmada de algum registro. */
    boolean existsByPhotoKey(String objectKey);

    Optional<MealLog> findById(UUID id);

    /** Os registros do paciente no plano, num dia, pela ordem das refeições. */
    List<MealLog> findByPlanAndDay(UUID clientId, UUID planId, LocalDate day);

    void delete(UUID id);
}
