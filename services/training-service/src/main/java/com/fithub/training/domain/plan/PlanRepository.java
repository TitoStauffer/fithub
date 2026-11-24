package com.fithub.training.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fichas de treino e as versões delas (DEV-0059). */
public interface PlanRepository {

    /** Grava o cabeçalho e a versão atual (com as divisões). */
    void save(WorkoutPlan plan);

    Optional<WorkoutPlan> findById(UUID id);

    /**
     * As fichas do personal para o aluno, da mais nova para a mais antiga. Com {@code personalId} nulo, as fichas
     * próprias do aluno (DEV-0069).
     */
    List<WorkoutPlan> findByClientAndPersonal(UUID clientId, UUID personalId);

    /** As fichas publicadas do aluno, da mais nova para a mais antiga (DEV-0065). */
    List<WorkoutPlan> findPublishedForClient(UUID clientId);

    /** A ficha publicada (ativa) do personal para o aluno, se houver; com {@code personalId} nulo, a própria. */
    Optional<WorkoutPlan> findPublished(UUID clientId, UUID personalId);

    /**
     * Fichas publicadas do personal, de alunos com vínculo ativo, que vencem até {@code until} (inclusive as já
     * vencidas), pela data de vencimento.
     */
    List<ExpiringPlan> expiring(UUID personalId, LocalDate until);

    /** As versões da ficha, da primeira para a última (DEV-0061). */
    List<VersionSummary> findVersions(UUID planId);

    /** Uma versão pelo id, com as divisões e os itens (a sessão guarda a versão em que foi feita, DEV-0066). */
    Optional<PlanVersion> findVersionById(UUID versionId);

    /** Uma versão da ficha pelo número, com as divisões e os itens. */
    Optional<PlanVersion> findVersion(UUID planId, int number);

    /** Resumo de uma versão: número, nome e quando foi publicada ({@code null} no rascunho). */
    record VersionSummary(int number, String name, Instant publishedAt) {}

    /** Ficha publicada perto do vencimento, com o nome do aluno da projeção local (pode faltar). */
    record ExpiringPlan(UUID planId, UUID clientId, String clientName, String name, LocalDate expiresOn) {}
}
