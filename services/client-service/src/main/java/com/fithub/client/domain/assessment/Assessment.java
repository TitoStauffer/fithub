package com.fithub.client.domain.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Avaliação corporal (DEV-0044, FR-14, FR-15). O cliente é o dono (BR-01); os profissionais vinculados veem
 * (BR-04). Imutável: corrige-se registrando outra.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: tipos e autores abaixo, faixas de peso e circunferências,
 * e {@code sexForFormulas} só para os cálculos de % de gordura (o gênero do perfil pode ser "outro" ou "prefiro não
 * informar").
 *
 * @param clientId o {@code sub} da conta do cliente (ADR-009)
 * @param authorId o {@code sub} de quem registrou
 * @param sexForFormulas sexo usado nas fórmulas de % de gordura, ou {@code null} (sem cálculo)
 */
public record Assessment(
        UUID id,
        UUID clientId,
        Type type,
        LocalDate measuredOn,
        BigDecimal weightKg,
        Circumferences circumferences,
        Sex sexForFormulas,
        UUID authorId,
        AuthorRole authorRole,
        Instant createdAt) {

    /** Presencial é do profissional; online (autoaferida) é do cliente (FR-15). */
    public enum Type {
        PRESENTIAL,
        ONLINE
    }

    public enum AuthorRole {
        CLIENT,
        PROFESSIONAL
    }

    public enum Sex {
        FEMALE,
        MALE
    }

    public static final BigDecimal MIN_WEIGHT = new BigDecimal("20");
    public static final BigDecimal MAX_WEIGHT = new BigDecimal("400");

    public Assessment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(authorRole, "authorRole");
        Objects.requireNonNull(createdAt, "createdAt");
        if (measuredOn == null) {
            throw new InvalidAssessmentException("measuredOn", "Informe a data da medição.");
        }
        Measures.requireInRange(
                "weightKg", weightKg, MIN_WEIGHT, MAX_WEIGHT, "O peso vai de 20 a 400 kg, com até 1 casa decimal.");
        circumferences = circumferences == null ? Circumferences.NONE : circumferences;
        if ((type == Type.PRESENTIAL) != (authorRole == AuthorRole.PROFESSIONAL)) {
            throw new IllegalArgumentException("Avaliação presencial é do profissional; online, do cliente.");
        }
    }

    /** Avaliação nova, com a data da medição conferida contra hoje: medição no futuro é recusada. */
    public static Assessment record(
            UUID id,
            UUID clientId,
            Type type,
            LocalDate measuredOn,
            BigDecimal weightKg,
            Circumferences circumferences,
            Sex sexForFormulas,
            UUID authorId,
            AuthorRole authorRole,
            LocalDate today,
            Instant now) {
        if (measuredOn != null && measuredOn.isAfter(today)) {
            throw new InvalidAssessmentException("measuredOn", "A data da medição não pode estar no futuro.");
        }
        return new Assessment(
                id, clientId, type, measuredOn, weightKg, circumferences, sexForFormulas, authorId, authorRole, now);
    }
}
