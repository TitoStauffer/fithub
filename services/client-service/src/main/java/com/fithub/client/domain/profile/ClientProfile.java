package com.fithub.client.domain.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Perfil do cliente (DEV-0032, FR-07, FR-09). O cliente é o dono (BR-01): só ele cria e altera; o
 * profissional vinculado lê pela DEV-0040.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: gênero e objetivo nos valores abaixo, altura de
 * 100 a 250 cm e idade mínima de 18 anos enquanto a QUESTÃO-05 (menores de idade) estiver aberta, porque
 * a LGPD (art. 14) exige o consentimento do responsável e o produto ainda não tem esse fluxo.
 *
 * @param accountId {@code sub} do token: o id da conta no Keycloak
 */
public record ClientProfile(
        String accountId,
        String name,
        LocalDate birthDate,
        Gender gender,
        int heightCm,
        Goal goal,
        Instant createdAt,
        Instant updatedAt) {

    /** Idade mínima no MVP (QUESTÃO-05 aberta). Trocar aqui libera menores quando houver o fluxo do responsável. */
    public static final int MINIMUM_AGE = 18;

    public static final int MAX_NAME = 200;
    public static final int MIN_HEIGHT_CM = 100;
    public static final int MAX_HEIGHT_CM = 250;

    public enum Gender {
        FEMALE,
        MALE,
        OTHER,
        PREFER_NOT_TO_SAY
    }

    public enum Goal {
        WEIGHT_LOSS,
        HYPERTROPHY,
        CONDITIONING,
        HEALTH,
        OTHER
    }

    public ClientProfile {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Perfil sem conta.");
        }
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            throw new InvalidProfileException("name", "Informe seu nome (até 200 caracteres).");
        }
        if (birthDate == null) {
            throw new InvalidProfileException("birthDate", "Informe sua data de nascimento.");
        }
        if (gender == null) {
            throw new InvalidProfileException("gender", "Informe o gênero (ou prefira não informar).");
        }
        if (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM) {
            throw new InvalidProfileException("heightCm", "Altura em centímetros, de 100 a 250.");
        }
        if (goal == null) {
            throw new InvalidProfileException("goal", "Informe seu objetivo.");
        }
    }

    /**
     * Perfil validado na data de hoje: nascimento no passado e idade mínima. O nome chega sem espaços nas
     * pontas.
     */
    public static ClientProfile of(
            String accountId,
            String name,
            LocalDate birthDate,
            Gender gender,
            int heightCm,
            Goal goal,
            LocalDate today,
            Instant createdAt,
            Instant updatedAt) {
        if (birthDate != null && !birthDate.isBefore(today)) {
            throw new InvalidProfileException("birthDate", "A data de nascimento precisa estar no passado.");
        }
        if (birthDate != null && Period.between(birthDate, today).getYears() < MINIMUM_AGE) {
            throw new InvalidProfileException(
                    "birthDate", "No momento o FitHub é só para maiores de %d anos.".formatted(MINIMUM_AGE));
        }
        return new ClientProfile(
                accountId, name == null ? null : name.strip(), birthDate, gender, heightCm, goal, createdAt, updatedAt);
    }

    /**
     * Se os dados que os outros serviços recebem no {@code ClienteAtualizado} (nome, nascimento e gênero)
     * são os mesmos nos dois perfis. Altura e objetivo ficam no client-service.
     */
    public boolean sameSharedDataAs(ClientProfile other) {
        return name.equals(other.name) && birthDate.equals(other.birthDate) && gender == other.gender;
    }

    /** Idade em anos completos na data dada (FR-09): calculada, nunca gravada. */
    public int ageOn(LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }
}
