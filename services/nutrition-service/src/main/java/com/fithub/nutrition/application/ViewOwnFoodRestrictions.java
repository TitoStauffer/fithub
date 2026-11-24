package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.restriction.FoodRestriction;
import com.fithub.nutrition.domain.restriction.FoodRestrictionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** O paciente lê as próprias restrições alimentares, só leitura (DEV-0080, FR-40). */
@Service
public class ViewOwnFoodRestrictions {

    private final FoodRestrictionRepository restrictions;

    public ViewOwnFoodRestrictions(FoodRestrictionRepository restrictions) {
        this.restrictions = restrictions;
    }

    /** @param clientAccount o {@code sub} do token; conta sem {@code sub} em UUID não tem restrições */
    public List<FoodRestriction> mine(String clientAccount) {
        UUID clientId = ViewFood.accountId(clientAccount);
        return clientId == null ? List.of() : restrictions.findByClient(clientId);
    }
}
