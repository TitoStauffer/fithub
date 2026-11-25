package com.fithub.nutrition.domain.log;

import com.fithub.nutrition.domain.plan.Meal;
import com.fithub.nutrition.domain.plan.MealItem;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Registro de uma refeição do plano num dia (DEV-0087, FR-50): feita, parcial ou não feita, com a alternativa usada em
 * cada alimento. Fica na versão do plano em que foi feito (BR-09) e só é corrigido no mesmo dia (BR-08).
 *
 * @param clientId o {@code sub} do paciente (ADR-009), dono do registro (BR-01)
 * @param loggedOn o dia do registro em America/Sao_Paulo
 * @param deviation o desvio em texto livre (DEV-0088, FR-51), até 500 caracteres
 * @param photo a foto do prato, se houver
 */
public record MealLog(
        UUID id,
        UUID clientId,
        UUID planId,
        UUID versionId,
        int versionNumber,
        int mealPosition,
        LocalDate loggedOn,
        Status status,
        List<Substitution> substitutions,
        String deviation,
        Photo photo,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MAX_DEVIATION_LENGTH = 500;
    public static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    public static final Set<String> PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    /** A foto do prato confirmada (DEV-0088): a chave do objeto no bucket privado e quando foi confirmada. */
    public record Photo(String objectKey, Instant uploadedAt) {}

    /** Feita, parcial ou não feita. */
    public enum Status {
        DONE,
        PARTIAL,
        SKIPPED
    }

    /**
     * A alternativa usada num alimento da refeição.
     *
     * @param itemPosition a posição do alimento na refeição
     * @param alternative 0 para a secundária, 1 para a terciária
     */
    public record Substitution(Integer itemPosition, Integer alternative) {}

    public MealLog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(loggedOn, "loggedOn");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        substitutions = substitutions == null ? List.of() : List.copyOf(substitutions);
    }

    /** O registro novo, conferido contra a versão atual do plano. */
    public static MealLog record(
            UUID id,
            UUID clientId,
            UUID planId,
            MealPlanVersion version,
            Integer mealPosition,
            Status status,
            List<Substitution> substitutions,
            String deviation,
            LocalDate today,
            Instant now) {
        List<Substitution> checked = checked(version, mealPosition, status, substitutions);
        return new MealLog(
                id,
                clientId,
                planId,
                version.id(),
                version.number(),
                mealPosition,
                today,
                status,
                checked,
                checkedDeviation(deviation),
                null,
                now,
                now);
    }

    /**
     * O registro corrigido, na mesma versão e na mesma refeição.
     *
     * @throws MealLogConflictException se o dia do registro já passou ou a refeição é outra
     */
    public MealLog correct(
            MealPlanVersion version,
            Integer meal,
            Status newStatus,
            List<Substitution> newSubstitutions,
            String newDeviation,
            LocalDate today,
            Instant now) {
        requireCorrectableOn(today);
        if (meal == null || meal != mealPosition) {
            throw new MealLogConflictException("Este registro é de outra refeição.");
        }
        List<Substitution> checked = checked(version, meal, newStatus, newSubstitutions);
        return new MealLog(
                id,
                clientId,
                planId,
                versionId,
                versionNumber,
                mealPosition,
                loggedOn,
                newStatus,
                checked,
                checkedDeviation(newDeviation),
                photo,
                createdAt,
                now);
    }

    /**
     * O registro com a foto do prato confirmada.
     *
     * @throws MealLogConflictException se o dia do registro já passou
     */
    public MealLog withPhoto(Photo newPhoto, LocalDate today, Instant now) {
        requireCorrectableOn(today);
        return new MealLog(
                id,
                clientId,
                planId,
                versionId,
                versionNumber,
                mealPosition,
                loggedOn,
                status,
                substitutions,
                deviation,
                Objects.requireNonNull(newPhoto, "newPhoto"),
                createdAt,
                now);
    }

    /**
     * O registro sem a foto do prato.
     *
     * @throws MealLogConflictException se o dia do registro já passou
     */
    public MealLog withoutPhoto(LocalDate today, Instant now) {
        requireCorrectableOn(today);
        return new MealLog(
                id,
                clientId,
                planId,
                versionId,
                versionNumber,
                mealPosition,
                loggedOn,
                status,
                substitutions,
                deviation,
                null,
                createdAt,
                now);
    }

    /** O desvio sem espaços nas pontas; vazio é sem desvio. */
    private static String checkedDeviation(String deviation) {
        if (deviation == null || deviation.isBlank()) {
            return null;
        }
        String stripped = deviation.strip();
        if (stripped.length() > MAX_DEVIATION_LENGTH) {
            throw new InvalidMealLogException("deviation", "O desvio pode ter até 500 caracteres.");
        }
        return stripped;
    }

    /** A foto precisa ser JPEG ou PNG. */
    public static String requirePhotoContentType(String contentType) {
        if (contentType == null || !PHOTO_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidMealLogException("contentType", "A foto precisa ser JPEG ou PNG.");
        }
        return contentType;
    }

    /** O registro só muda no dia em que foi feito (BR-08, QUESTÃO-04). */
    public void requireCorrectableOn(LocalDate today) {
        if (!loggedOn.equals(today)) {
            throw new MealLogConflictException("O registro só pode ser corrigido no dia em que foi feito.");
        }
    }

    private static List<Substitution> checked(
            MealPlanVersion version, Integer mealPosition, Status status, List<Substitution> substitutions) {
        if (status == null) {
            throw new InvalidMealLogException("status", "Informe se a refeição foi feita, parcial ou não feita.");
        }
        if (mealPosition == null
                || mealPosition < 0
                || mealPosition >= version.meals().size()) {
            throw new InvalidMealLogException("mealPosition", "A refeição não existe no plano.");
        }
        List<Substitution> given = substitutions == null ? List.of() : substitutions;
        if (status == Status.SKIPPED && !given.isEmpty()) {
            throw new InvalidMealLogException("substitutions", "A refeição não feita não tem alternativa usada.");
        }
        Meal meal = version.meals().get(mealPosition);
        Set<Integer> seen = new HashSet<>();
        List<Substitution> checked = new ArrayList<>();
        for (int index = 0; index < given.size(); index++) {
            checked.add(checkedSubstitution(meal, given.get(index), "substitutions[" + index + "]", seen));
        }
        return checked;
    }

    private static Substitution checkedSubstitution(
            Meal meal, Substitution substitution, String path, Set<Integer> seen) {
        if (substitution == null) {
            throw new InvalidMealLogException(path, "Informe o alimento e a alternativa usada.");
        }
        Integer item = substitution.itemPosition();
        if (item == null || item < 0 || item >= meal.items().size() || !seen.add(item)) {
            throw new InvalidMealLogException(
                    path + ".itemPosition", "O alimento não existe na refeição ou já tem alternativa marcada.");
        }
        MealItem mealItem = meal.items().get(item);
        Integer alternative = substitution.alternative();
        if (alternative == null
                || alternative < 0
                || alternative >= mealItem.alternatives().size()) {
            throw new InvalidMealLogException(path + ".alternative", "Esta alternativa não existe para o alimento.");
        }
        return substitution;
    }
}
