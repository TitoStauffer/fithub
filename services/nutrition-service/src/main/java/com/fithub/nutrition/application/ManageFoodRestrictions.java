package com.fithub.nutrition.application;

import com.fithub.commons.ids.UuidV7;
import com.fithub.nutrition.domain.access.ProfessionalAccessAudit;
import com.fithub.nutrition.domain.restriction.FoodRestriction;
import com.fithub.nutrition.domain.restriction.FoodRestrictionRepository;
import com.fithub.nutrition.domain.restriction.InvalidRestrictionException;
import com.fithub.nutrition.domain.restriction.RestrictionNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Restrições alimentares do paciente pela nutricionista vinculada (DEV-0080, FR-40). Toda operação passa pela
 * verificação de acesso por vínculo (BR-02): sem vínculo ativo, 404. Até 30 por paciente.
 */
@Service
public class ManageFoodRestrictions {

    private final ClientAccess clientAccess;
    private final FoodRestrictionRepository restrictions;
    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public ManageFoodRestrictions(
            ClientAccess clientAccess,
            FoodRestrictionRepository restrictions,
            ProfessionalAccessAudit audit,
            Clock clock) {
        this.clientAccess = clientAccess;
        this.restrictions = restrictions;
        this.audit = audit;
        this.clock = clock;
    }

    /** O que a nutricionista informa. */
    public record Draft(FoodRestriction.Kind kind, String description, List<String> terms) {}

    /** Cada leitura pela nutricionista fica registrada (DEV-0171, NFR-03): é dado de saúde do paciente. */
    public List<FoodRestriction> list(String nutritionistAccount, UUID clientId) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        List<FoodRestriction> found = restrictions.findByClient(clientId);
        audit.record(
                UUID.fromString(nutritionistAccount),
                clientId,
                ProfessionalAccessAudit.Resource.FOOD_RESTRICTIONS,
                clock.instant());
        return found;
    }

    public FoodRestriction create(String nutritionistAccount, UUID clientId, Draft draft) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        FoodRestriction restriction = FoodRestriction.record(
                UuidV7.next(clock),
                clientId,
                draft.kind(),
                draft.description(),
                draft.terms(),
                UUID.fromString(nutritionistAccount),
                clock.instant());
        if (restrictions.findByClient(clientId).size() >= FoodRestriction.MAX_PER_CLIENT) {
            throw new InvalidRestrictionException("restrictions", "O paciente tem até 30 restrições.");
        }
        restrictions.save(restriction);
        return restriction;
    }

    public FoodRestriction update(String nutritionistAccount, UUID clientId, UUID restrictionId, Draft draft) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        FoodRestriction changed = ofClient(clientId, restrictionId)
                .change(
                        draft.kind(),
                        draft.description(),
                        draft.terms(),
                        UUID.fromString(nutritionistAccount),
                        clock.instant());
        restrictions.save(changed);
        return changed;
    }

    public void remove(String nutritionistAccount, UUID clientId, UUID restrictionId) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        restrictions.delete(ofClient(clientId, restrictionId).id());
    }

    /** A restrição pelo id, só se for deste paciente: a de outro responde como inexistente. */
    private FoodRestriction ofClient(UUID clientId, UUID restrictionId) {
        return restrictions
                .findById(restrictionId)
                .filter(restriction -> restriction.clientId().equals(clientId))
                .orElseThrow(RestrictionNotFoundException::new);
    }
}
