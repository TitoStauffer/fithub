package com.fithub.client.domain.profile;

import java.util.Optional;

/** Persistência dos perfis de saúde (porta do domínio). */
public interface HealthProfileRepository {

    Optional<HealthProfile> findByAccountId(String accountId);

    /**
     * Cria ou substitui o perfil de saúde da conta numa operação só, mantendo a data de criação do
     * primeiro. Devolve o perfil gravado.
     */
    HealthProfile save(HealthProfile profile);
}
