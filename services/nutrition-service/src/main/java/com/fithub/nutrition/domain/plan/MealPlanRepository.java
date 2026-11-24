package com.fithub.nutrition.domain.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planos alimentares e as versões deles (DEV-0081). */
public interface MealPlanRepository {

    /** Grava o cabeçalho e a versão atual (com as refeições e os itens). */
    void save(MealPlan plan);

    Optional<MealPlan> findById(UUID id);

    /** Os planos da nutricionista para o paciente, do mais novo para o mais antigo. */
    List<MealPlan> findByClientAndNutritionist(UUID clientId, UUID nutritionistId);

    /** O plano publicado (ativo) do paciente, se houver. */
    Optional<MealPlan> findPublished(UUID clientId);
}
