package com.fithub.nutrition.domain.restriction;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Restrição alimentar ou preferência do paciente (DEV-0080, FR-40): tipo, descrição e os termos que o alerta do plano
 * procura no nome dos alimentos (DEV-0084). É do paciente (BR-01): a nutricionista vinculada registra.
 *
 * @param clientId o {@code sub} do paciente (ADR-009)
 * @param terms de 1 a 10 termos, sem repetir (sem diferenciar maiúsculas)
 * @param recordedBy o {@code sub} da nutricionista que registrou ou editou por último
 */
public record FoodRestriction(
        UUID id,
        UUID clientId,
        Kind kind,
        String description,
        List<String> terms,
        UUID recordedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MIN_DESCRIPTION_LENGTH = 2;
    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_TERMS = 10;
    public static final int MIN_TERM_LENGTH = 2;
    public static final int MAX_TERM_LENGTH = 40;
    /** Restrições por paciente. */
    public static final int MAX_PER_CLIENT = 30;

    /** Alergia e intolerância geram alerta no plano (BR-11); a preferência só aparece para a nutricionista. */
    public enum Kind {
        ALLERGY,
        INTOLERANCE,
        PREFERENCE;

        public boolean alerts() {
            return this != PREFERENCE;
        }
    }

    /**
     * Se o alimento com esse nome dispara o alerta desta restrição (DEV-0084, FR-41): só alergia e intolerância, e
     * algum termo aparece no nome, sem diferenciar maiúsculas nem acentos ("feijao" acha "Feijão, carioca").
     */
    public boolean alertsFor(String foodName) {
        if (!kind.alerts() || foodName == null) {
            return false;
        }
        String name = folded(foodName);
        return terms.stream().map(FoodRestriction::folded).anyMatch(name::contains);
    }

    /** Minúsculas e sem acento, como o {@code fold_search} da busca (V6). */
    static String folded(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    public FoodRestriction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(recordedBy, "recordedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (kind == null) {
            throw new InvalidRestrictionException("kind", "Escolha o tipo: alergia, intolerância ou preferência.");
        }
        description = description == null ? "" : description.strip();
        if (description.length() < MIN_DESCRIPTION_LENGTH || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidRestrictionException("description", "A descrição precisa ter de 2 a 200 caracteres.");
        }
        terms = checkedTerms(terms);
    }

    private static List<String> checkedTerms(List<String> terms) {
        if (terms == null || terms.isEmpty() || terms.size() > MAX_TERMS) {
            throw new InvalidRestrictionException("terms", "Informe de 1 a 10 termos.");
        }
        List<String> checked = new ArrayList<>();
        for (int index = 0; index < terms.size(); index++) {
            String term = terms.get(index) == null ? "" : terms.get(index).strip();
            if (term.length() < MIN_TERM_LENGTH || term.length() > MAX_TERM_LENGTH) {
                throw new InvalidRestrictionException(
                        "terms[" + index + "]", "Cada termo precisa ter de 2 a 40 caracteres.");
            }
            String folded = term.toLowerCase(Locale.ROOT);
            if (checked.stream()
                    .anyMatch(other -> other.toLowerCase(Locale.ROOT).equals(folded))) {
                throw new InvalidRestrictionException("terms[" + index + "]", "Esse termo já está na lista.");
            }
            checked.add(term);
        }
        return List.copyOf(checked);
    }

    /** Nova restrição registrada pela nutricionista. */
    public static FoodRestriction record(
            UUID id,
            UUID clientId,
            Kind kind,
            String description,
            List<String> terms,
            UUID nutritionistId,
            Instant now) {
        return new FoodRestriction(id, clientId, kind, description, terms, nutritionistId, now, now);
    }

    /** A mesma restrição com o tipo, a descrição e os termos novos. */
    public FoodRestriction change(
            Kind newKind, String newDescription, List<String> newTerms, UUID nutritionistId, Instant now) {
        return new FoodRestriction(id, clientId, newKind, newDescription, newTerms, nutritionistId, createdAt, now);
    }
}
