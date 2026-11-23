package com.fithub.account.domain.professional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Profissional do FitHub (DEV-0029): uma conta do Keycloak que se cadastrou como personal ou
 * nutricionista. Cada conta tem no máximo um cadastro, e cada profissional nasce dono de uma
 * organização de uma pessoa (academias ficam fora do MVP, mas o modelo já tem organização).
 *
 * <p>A conta mantém o papel de cliente (decisão do Tito, QUESTÃO-02) e ganha o de profissional. A
 * atribuição do papel no Keycloak acontece depois do cadastro; até ela terminar, {@link #roleStatus()}
 * fica {@link RoleStatus#PENDING}.
 *
 * @param id o mesmo valor da conta: o {@code sub} do Keycloak é o id da pessoa em todos os serviços (ADR-009)
 * @param accountId {@code sub} do token: o id da conta no Keycloak
 * @param name nome de exibição, vindo do perfil do Keycloak no cadastro (DEV-0030)
 */
public record Professional(
        UUID id,
        String accountId,
        String name,
        Type type,
        Registration registration,
        Situation situation,
        UUID organizationId,
        RoleStatus roleStatus,
        Instant createdAt) {

    /** Tipo de profissional e o conselho que o habilita (PRD §3). */
    public enum Type {
        PERSONAL(Registration.Council.CREF),
        NUTRITIONIST(Registration.Council.CRN);

        private final Registration.Council council;

        Type(Registration.Council council) {
            this.council = council;
        }

        public Registration.Council council() {
            return council;
        }
    }

    /** Situação do registro no conselho. No MVP só existe a declarada (QUESTÃO-08). */
    public enum Situation {
        DECLARED
    }

    /** Se o papel de profissional já foi atribuído à conta no Keycloak. */
    public enum RoleStatus {
        PENDING,
        ASSIGNED
    }

    public Professional {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(situation, "situation");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(roleStatus, "roleStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!id.equals(idOf(accountId))) {
            throw new IllegalArgumentException("O id do profissional é o id da conta (ADR-009).");
        }
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            throw new InvalidRegistrationException("name", "Complete seu nome no perfil (até 200 caracteres).");
        }
        if (registration.council() != type.council()) {
            throw new InvalidRegistrationException(
                    "registrationNumber", "%s se registra no %s.".formatted(label(type), type.council()));
        }
    }

    /** Tamanho máximo do nome, o mesmo do contrato do evento {@code account.professional-registered.v1}. */
    public static final int MAX_NAME = 200;

    /** Cadastro novo: registro declarado e papel ainda por atribuir. O nome chega sem espaços nas pontas. */
    public static Professional register(
            String accountId, String name, Type type, Registration registration, UUID organizationId, Instant now) {
        return new Professional(
                idOf(accountId),
                accountId,
                name == null ? null : name.strip(),
                type,
                registration,
                Situation.DECLARED,
                organizationId,
                RoleStatus.PENDING,
                now);
    }

    /**
     * Id do profissional da conta: o próprio {@code sub} do Keycloak (ADR-009). Assim training, nutrition e
     * client reconhecem o profissional pelo token, sem projeção da conta.
     */
    public static UUID idOf(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Profissional sem conta.");
        }
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException notUuid) {
            throw new IllegalArgumentException("Conta fora do formato do Keycloak (UUID).", notUuid);
        }
    }

    /** O pedido repete o cadastro que já existe (mesmo tipo e mesmo registro). */
    public boolean sameAs(Type otherType, Registration otherRegistration) {
        return type == otherType && registration.equals(otherRegistration);
    }

    public Professional withRoleAssigned() {
        return new Professional(
                id, accountId, name, type, registration, situation, organizationId, RoleStatus.ASSIGNED, createdAt);
    }

    private static String label(Type type) {
        return type == Type.PERSONAL ? "Personal" : "Nutricionista";
    }
}
