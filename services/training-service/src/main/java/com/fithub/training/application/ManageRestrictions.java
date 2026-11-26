package com.fithub.training.application;

import com.fithub.training.domain.access.ProfessionalAccessAudit;
import com.fithub.training.domain.restriction.BodyRegion;
import com.fithub.training.domain.restriction.MobilityRestriction;
import com.fithub.training.domain.restriction.RestrictionNotFoundException;
import com.fithub.training.domain.restriction.RestrictionRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Restrições de mobilidade do aluno pelo personal vinculado (DEV-0058, FR-26). Toda operação passa pela verificação
 * de acesso por vínculo (BR-02): sem vínculo ativo, 404.
 */
@Service
public class ManageRestrictions {

    private final ClientAccess clientAccess;
    private final RestrictionRepository restrictions;
    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public ManageRestrictions(
            ClientAccess clientAccess, RestrictionRepository restrictions, ProfessionalAccessAudit audit, Clock clock) {
        this.clientAccess = clientAccess;
        this.restrictions = restrictions;
        this.audit = audit;
        this.clock = clock;
    }

    /** Cada leitura pelo personal fica registrada (DEV-0170, NFR-03): é dado de saúde do aluno. */
    public List<MobilityRestriction> list(String personalAccount, UUID clientId) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        List<MobilityRestriction> found = restrictions.findByClient(clientId);
        audit.record(
                UUID.fromString(personalAccount),
                clientId,
                ProfessionalAccessAudit.Resource.MOBILITY_RESTRICTIONS,
                clock.instant());
        return found;
    }

    public MobilityRestriction create(String personalAccount, UUID clientId, BodyRegion region, String description) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        MobilityRestriction restriction = MobilityRestriction.record(
                clientId, region, description, UUID.fromString(personalAccount), clock.instant());
        restrictions.save(restriction);
        return restriction;
    }

    public MobilityRestriction update(
            String personalAccount, UUID clientId, UUID restrictionId, BodyRegion region, String description) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        MobilityRestriction changed = ofClient(clientId, restrictionId)
                .change(region, description, UUID.fromString(personalAccount), clock.instant());
        restrictions.save(changed);
        return changed;
    }

    public void remove(String personalAccount, UUID clientId, UUID restrictionId) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        restrictions.delete(ofClient(clientId, restrictionId).id());
    }

    /** A restrição pelo id, só se for deste aluno: a de outro responde como inexistente. */
    private MobilityRestriction ofClient(UUID clientId, UUID restrictionId) {
        return restrictions
                .findById(restrictionId)
                .filter(restriction -> restriction.clientId().equals(clientId))
                .orElseThrow(RestrictionNotFoundException::new);
    }
}
