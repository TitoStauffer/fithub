package com.fithub.training.application;

import com.fithub.training.domain.restriction.MobilityRestriction;
import com.fithub.training.domain.restriction.RestrictionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** O aluno lê as próprias restrições de mobilidade (DEV-0058, FR-26), nunca as de outro. */
@Service
public class ViewOwnRestrictions {

    private final RestrictionRepository restrictions;

    public ViewOwnRestrictions(RestrictionRepository restrictions) {
        this.restrictions = restrictions;
    }

    /** @param clientAccount o {@code sub} do token, que é o id do aluno (ADR-009); fora do formato, lista vazia */
    public List<MobilityRestriction> list(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return clientId == null ? List.of() : restrictions.findByClient(clientId);
    }
}
