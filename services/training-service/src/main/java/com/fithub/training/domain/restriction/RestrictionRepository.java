package com.fithub.training.domain.restriction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Restrições de mobilidade dos alunos (DEV-0058). */
public interface RestrictionRepository {

    void save(MobilityRestriction restriction);

    Optional<MobilityRestriction> findById(UUID id);

    /** Do aluno, da mais antiga para a mais nova. */
    List<MobilityRestriction> findByClient(UUID clientId);

    void delete(UUID id);
}
