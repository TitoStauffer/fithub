package com.fithub.client.domain.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das avaliações corporais (porta do domínio). */
public interface AssessmentRepository {

    void save(Assessment assessment);

    Optional<Assessment> findById(UUID id);

    /** Avaliações do cliente, da medição mais recente para a mais antiga. */
    List<Assessment> listByClient(UUID clientId);

    void delete(UUID id);
}
