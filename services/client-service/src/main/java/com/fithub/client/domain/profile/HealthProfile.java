package com.fithub.client.domain.profile;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Perfil de saúde do cliente (DEV-0033, FR-08). Dado pessoal sensível (LGPD, art. 11): o cliente é o dono
 * (BR-01), nada dele vai para log, mensagem de erro ou evento, e só o profissional vinculado lê (DEV-0040).
 *
 * <p>Decisão do Agent na ausência do Tito, para revisão: as condições abaixo, mais um texto livre de até
 * 1000 caracteres.
 *
 * @param conditions condições marcadas, sem repetição (pode ser vazio: nenhuma condição)
 * @param notes texto livre, ou {@code null} quando não houver
 */
public record HealthProfile(
        String accountId, Set<Condition> conditions, String notes, Instant createdAt, Instant updatedAt) {

    public static final int MAX_NOTES = 1000;

    public enum Condition {
        DIABETES,
        HYPERTENSION,
        PREGNANCY,
        RECENT_SURGERY,
        OTHER
    }

    public HealthProfile {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Perfil de saúde sem conta.");
        }
        if (conditions == null) {
            throw new InvalidProfileException("conditions", "Informe as condições (ou nenhuma).");
        }
        if (notes != null && notes.length() > MAX_NOTES) {
            // A mensagem nunca repete o texto: é dado de saúde.
            throw new InvalidProfileException("notes", "O texto livre pode ter até 1000 caracteres.");
        }
        conditions = Collections.unmodifiableSet(
                conditions.isEmpty() ? EnumSet.noneOf(Condition.class) : EnumSet.copyOf(conditions));
    }

    /**
     * Perfil de saúde a partir do que a pessoa enviou: condições repetidas viram uma só, ausência de
     * condições é nenhuma, e o texto livre chega sem espaços nas pontas ({@code null} se ficar vazio).
     */
    public static HealthProfile of(
            String accountId, Collection<Condition> conditions, String notes, Instant createdAt, Instant updatedAt) {
        Set<Condition> unique = EnumSet.noneOf(Condition.class);
        if (conditions != null) {
            for (Condition condition : conditions) {
                if (condition == null) {
                    throw new InvalidProfileException("conditions", "Condição vazia na lista.");
                }
                unique.add(condition);
            }
        }
        String trimmed = notes == null || notes.isBlank() ? null : notes.strip();
        return new HealthProfile(accountId, unique, trimmed, createdAt, updatedAt);
    }
}
