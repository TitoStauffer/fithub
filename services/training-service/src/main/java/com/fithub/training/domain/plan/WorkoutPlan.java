package com.fithub.training.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ficha de treino que o personal monta para o aluno (DEV-0059, FR-28, FR-32, FR-34). Começa em rascunho, é
 * publicada e fica substituída quando o personal publica outra para o mesmo aluno: uma ficha ativa por vez.
 *
 * @param clientId o {@code sub} do aluno (ADR-009)
 * @param personalId o {@code sub} do personal autor; só ele edita e publica
 * @param current a versão atual do conteúdo
 */
public record WorkoutPlan(
        UUID id,
        UUID clientId,
        UUID personalId,
        Status status,
        PlanVersion current,
        Instant createdAt,
        Instant updatedAt) {

    /** Rascunho, publicada (a ativa) ou substituída por outra publicada depois. */
    public enum Status {
        DRAFT,
        PUBLISHED,
        REPLACED
    }

    public WorkoutPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(personalId, "personalId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((status == Status.DRAFT) == current.published()) {
            throw new IllegalArgumentException("Só o rascunho tem a versão atual sem publicação.");
        }
    }

    /** Nova ficha em rascunho, na versão 1. */
    public static WorkoutPlan newDraft(
            UUID clientId,
            UUID personalId,
            String name,
            LocalDate startsOn,
            int validityWeeks,
            List<PlanVersion.DivisionDraft> divisions,
            Instant now) {
        PlanVersion first = new PlanVersion(
                UUID.randomUUID(), 1, name, startsOn, validityWeeks, PlanVersion.divisions(divisions), null, now);
        return new WorkoutPlan(UUID.randomUUID(), clientId, personalId, Status.DRAFT, first, now, now);
    }

    public boolean authoredBy(UUID personal) {
        return personalId.equals(personal);
    }

    /**
     * O rascunho com o conteúdo novo, na mesma versão.
     *
     * @throws PlanStateException se a ficha já foi publicada (editar a publicada é a DEV-0061)
     */
    public WorkoutPlan editDraft(
            String name,
            LocalDate startsOn,
            int validityWeeks,
            List<PlanVersion.DivisionDraft> divisions,
            Instant now) {
        if (status != Status.DRAFT) {
            throw new PlanStateException("Só o rascunho pode ser editado assim.");
        }
        PlanVersion content = new PlanVersion(
                current.id(),
                current.number(),
                name,
                startsOn,
                validityWeeks,
                PlanVersion.divisions(divisions),
                null,
                current.createdAt());
        return new WorkoutPlan(id, clientId, personalId, status, content, createdAt, now);
    }

    /**
     * Publica o rascunho: o aluno passa a ver a ficha.
     *
     * @throws PlanStateException se a ficha não é rascunho
     * @throws InvalidPlanException se alguma divisão não tem exercício (BR-10)
     */
    public WorkoutPlan publish(Instant now) {
        if (status != Status.DRAFT) {
            throw new PlanStateException("A ficha já foi publicada.");
        }
        requireExerciseInEveryDivision(current);
        PlanVersion published = new PlanVersion(
                current.id(),
                current.number(),
                current.name(),
                current.startsOn(),
                current.validityWeeks(),
                current.divisions(),
                now,
                current.createdAt());
        return new WorkoutPlan(id, clientId, personalId, Status.PUBLISHED, published, createdAt, now);
    }

    /** Publicar exige ao menos um exercício em cada divisão (BR-10). */
    private static void requireExerciseInEveryDivision(PlanVersion version) {
        for (int position = 0; position < version.divisions().size(); position++) {
            if (version.divisions().get(position).items().isEmpty()) {
                throw new InvalidPlanException(
                        "divisions[" + position + "].items", "Cada divisão precisa de ao menos um exercício.");
            }
        }
    }

    /**
     * Edita a ficha (DEV-0061, FR-33, BR-09). O rascunho muda no lugar; a ficha publicada ganha a versão seguinte, já
     * publicada, e a anterior nunca é alterada.
     *
     * @param baseVersion a versão em que a edição se baseou; obrigatória na ficha publicada
     * @throws PlanConflictException se outra edição chegou antes (a versão atual não é mais a da base)
     * @throws PlanStateException se a ficha foi substituída
     */
    public WorkoutPlan edit(
            Integer baseVersion,
            String name,
            LocalDate startsOn,
            int validityWeeks,
            List<PlanVersion.DivisionDraft> divisions,
            Instant now) {
        if (status == Status.REPLACED) {
            throw new PlanStateException("Ficha substituída não pode ser editada.");
        }
        if (baseVersion != null && baseVersion != current.number()) {
            throw new PlanConflictException();
        }
        if (status == Status.DRAFT) {
            return editDraft(name, startsOn, validityWeeks, divisions, now);
        }
        if (baseVersion == null) {
            throw new InvalidPlanException("baseVersion", "Informe a versão em que a edição se baseou.");
        }
        PlanVersion next = new PlanVersion(
                UUID.randomUUID(),
                current.number() + 1,
                name,
                startsOn,
                validityWeeks,
                PlanVersion.divisions(divisions),
                now,
                now);
        requireExerciseInEveryDivision(next);
        return new WorkoutPlan(id, clientId, personalId, status, next, createdAt, now);
    }

    /** Sai de ativa porque o personal publicou outra ficha para o mesmo aluno. */
    public WorkoutPlan replace(Instant now) {
        if (status != Status.PUBLISHED) {
            throw new PlanStateException("Só a ficha publicada pode ser substituída.");
        }
        return new WorkoutPlan(id, clientId, personalId, Status.REPLACED, current, createdAt, now);
    }

    public LocalDate expiresOn() {
        return current.expiresOn();
    }
}
