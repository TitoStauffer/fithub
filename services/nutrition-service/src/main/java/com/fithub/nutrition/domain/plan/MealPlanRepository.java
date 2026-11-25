package com.fithub.nutrition.domain.plan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planos alimentares e as versões deles (DEV-0081). */
public interface MealPlanRepository {

    /** Grava o plano novo: o cabeçalho e a versão atual, com as refeições e os itens. */
    void add(MealPlan plan);

    /**
     * Grava o conteúdo do rascunho editado.
     *
     * @param readUpdatedAt o {@code updatedAt} do plano quando foi lido
     * @throws MealPlanStateException se o plano não é mais rascunho ou mudou desde a leitura
     */
    void saveDraft(MealPlan plan, Instant readUpdatedAt);

    /**
     * Grava o estado novo (publicado ou substituído) e a data de publicação, sem mexer nas refeições.
     *
     * @param readUpdatedAt o {@code updatedAt} do plano quando foi lido
     * @throws MealPlanStateException se o plano mudou desde a leitura
     */
    void saveStatus(MealPlan plan, Instant readUpdatedAt);

    /**
     * Grava a versão nova do plano publicado (DEV-0083); as anteriores ficam como estão (BR-09).
     *
     * @param readUpdatedAt o {@code updatedAt} do plano quando foi lido
     * @throws MealPlanStateException se o plano mudou desde a leitura
     */
    void addVersion(MealPlan plan, Instant readUpdatedAt);

    /** As versões do plano, da primeira para a última. */
    List<VersionSummary> findVersions(UUID planId);

    /** Uma versão do plano pelo número, com as refeições e os itens. */
    Optional<MealPlanVersion> findVersion(UUID planId, int number);

    /** Resumo de uma versão: número, nome e quando foi publicada ({@code null} no rascunho). */
    record VersionSummary(int number, String name, Instant publishedAt) {}

    Optional<MealPlan> findById(UUID id);

    /** Os planos da nutricionista para o paciente, do mais novo para o mais antigo. */
    List<MealPlan> findByClientAndNutritionist(UUID clientId, UUID nutritionistId);

    /** O plano publicado (ativo) do paciente, se houver. */
    Optional<MealPlan> findPublished(UUID clientId);
}
