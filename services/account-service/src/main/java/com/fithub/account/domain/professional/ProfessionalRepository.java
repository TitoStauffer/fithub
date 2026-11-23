package com.fithub.account.domain.professional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos profissionais e das organizações (porta do domínio). */
public interface ProfessionalRepository {

    Optional<Professional> findByAccountId(String accountId);

    /**
     * Grava o profissional novo e a organização dele juntos. Lança {@link AlreadyRegisteredException}
     * se a conta já tiver cadastro (inclusive quando dois pedidos chegam ao mesmo tempo).
     */
    void saveNew(Professional professional, Organization organization);

    void markRoleAssigned(UUID professionalId);

    /** Profissionais cujo papel ainda não foi atribuído no Keycloak, do mais antigo para o mais novo. */
    List<Professional> findPendingRoles(int limit);

    /** A conta já tem cadastro de profissional. */
    final class AlreadyRegisteredException extends RuntimeException {

        public AlreadyRegisteredException(String accountId) {
            super("A conta já tem cadastro de profissional: " + accountId);
        }
    }
}
