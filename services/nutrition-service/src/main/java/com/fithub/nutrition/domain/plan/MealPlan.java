package com.fithub.nutrition.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano alimentar que a nutricionista monta para o paciente (DEV-0081, FR-42 a FR-45). Começa em rascunho, é publicado
 * e fica substituído quando outro plano é publicado para o mesmo paciente: um ativo por vez.
 *
 * @param clientId o {@code sub} do paciente (ADR-009)
 * @param nutritionistId o {@code sub} da nutricionista autora; só ela edita e publica
 */
public record MealPlan(
        UUID id,
        UUID clientId,
        UUID nutritionistId,
        Status status,
        MealPlanVersion current,
        Instant createdAt,
        Instant updatedAt) {

    /** Rascunho, publicado (o ativo) ou substituído por outro publicado depois. */
    public enum Status {
        DRAFT,
        PUBLISHED,
        REPLACED
    }

    public MealPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(nutritionistId, "nutritionistId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((status == Status.DRAFT) == current.published()) {
            throw new IllegalArgumentException("Só o rascunho tem a versão atual sem publicação.");
        }
    }

    /** Novo plano em rascunho, na versão 1. */
    public static MealPlan newDraft(
            UUID id,
            UUID versionId,
            UUID clientId,
            UUID nutritionistId,
            String name,
            LocalDate startsOn,
            String note,
            List<Meal.Draft> meals,
            Instant now) {
        MealPlanVersion first =
                new MealPlanVersion(versionId, 1, name, startsOn, note, MealPlanVersion.meals(meals), null, now);
        return new MealPlan(id, clientId, nutritionistId, Status.DRAFT, first, now, now);
    }

    public boolean authoredBy(UUID nutritionist) {
        return nutritionistId.equals(nutritionist);
    }

    /**
     * O rascunho com o conteúdo novo, na mesma versão.
     *
     * @throws MealPlanStateException se o plano já foi publicado (editar o publicado gera versão, DEV-0083)
     */
    public MealPlan editDraft(String name, LocalDate startsOn, String note, List<Meal.Draft> meals, Instant now) {
        if (status != Status.DRAFT) {
            throw new MealPlanStateException("Só o rascunho pode ser editado assim.");
        }
        MealPlanVersion content = new MealPlanVersion(
                current.id(),
                current.number(),
                name,
                startsOn,
                note,
                MealPlanVersion.meals(meals),
                null,
                current.createdAt());
        return new MealPlan(id, clientId, nutritionistId, status, content, createdAt, now);
    }

    /**
     * Publica o rascunho: o paciente passa a ver o plano.
     *
     * @throws MealPlanStateException se o plano não é rascunho
     * @throws InvalidMealPlanException se alguma refeição não tem alimento (BR-10)
     */
    public MealPlan publish(Instant now) {
        if (status != Status.DRAFT) {
            throw new MealPlanStateException("O plano já foi publicado.");
        }
        requireFoodInEveryMeal(current);
        MealPlanVersion published = new MealPlanVersion(
                current.id(),
                current.number(),
                current.name(),
                current.startsOn(),
                current.note(),
                current.meals(),
                now,
                current.createdAt());
        return new MealPlan(id, clientId, nutritionistId, Status.PUBLISHED, published, createdAt, now);
    }

    /** Publicar exige ao menos um alimento em cada refeição (BR-10). */
    private static void requireFoodInEveryMeal(MealPlanVersion version) {
        for (Meal meal : version.meals()) {
            if (meal.items().isEmpty()) {
                throw new InvalidMealPlanException(
                        "meals[" + meal.position() + "].items", "Cada refeição precisa de ao menos um alimento.");
            }
        }
    }

    /**
     * Edita o plano (DEV-0083, FR-46, BR-09). O rascunho muda no lugar; o publicado ganha a versão seguinte, já
     * publicada, e a anterior nunca muda.
     *
     * @param baseVersion a versão em que a edição se baseou; obrigatória no plano publicado
     * @param nextVersionId o id da versão nova, usado só quando o plano já foi publicado
     * @throws MealPlanStateException se o plano foi substituído ou outra edição chegou antes (a versão atual não é a
     *     da base)
     * @throws InvalidMealPlanException se falta a versão base no publicado ou alguma refeição da versão nova não tem
     *     alimento (BR-10)
     */
    public MealPlan edit(
            Integer baseVersion,
            UUID nextVersionId,
            String name,
            LocalDate startsOn,
            String note,
            List<Meal.Draft> meals,
            Instant now) {
        if (status == Status.REPLACED) {
            throw new MealPlanStateException("Plano substituído não pode ser editado.");
        }
        if (baseVersion != null && baseVersion != current.number()) {
            throw new MealPlanStateException(
                    "Outra edição chegou antes: o plano já está na versão " + current.number() + ". Recarregue.");
        }
        if (status == Status.DRAFT) {
            return editDraft(name, startsOn, note, meals, now);
        }
        if (baseVersion == null) {
            throw new InvalidMealPlanException("baseVersion", "Informe a versão em que a edição se baseou.");
        }
        MealPlanVersion next = new MealPlanVersion(
                nextVersionId, current.number() + 1, name, startsOn, note, MealPlanVersion.meals(meals), now, now);
        requireFoodInEveryMeal(next);
        return new MealPlan(id, clientId, nutritionistId, status, next, createdAt, now);
    }

    /** Sai de ativo porque outro plano foi publicado para o mesmo paciente. */
    public MealPlan replace(Instant now) {
        if (status != Status.PUBLISHED) {
            throw new MealPlanStateException("Só o plano publicado pode ser substituído.");
        }
        return new MealPlan(id, clientId, nutritionistId, Status.REPLACED, current, createdAt, now);
    }
}
