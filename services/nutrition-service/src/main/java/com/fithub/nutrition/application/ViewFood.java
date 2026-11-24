package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodNotFoundException;
import com.fithub.nutrition.domain.food.FoodRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Alimento visto pela nutricionista (DEV-0074): a TACO e os próprios dela (BR-13). */
@Service
public class ViewFood {

    private final FoodRepository foods;

    public ViewFood(FoodRepository foods) {
        this.foods = foods;
    }

    /**
     * Alimento visível para a nutricionista; o próprio de outra responde como inexistente.
     *
     * @param nutritionistAccount o {@code sub} do token (ADR-009)
     */
    public Food view(String nutritionistAccount, UUID foodId) {
        UUID nutritionistId = accountId(nutritionistAccount);
        return foods.findById(foodId)
                .filter(food -> food.visibleTo(nutritionistId))
                .orElseThrow(FoodNotFoundException::new);
    }

    /** Conta sem {@code sub} em UUID não é dona de nada: vê só a TACO. */
    static UUID accountId(String account) {
        if (account == null) {
            return null;
        }
        try {
            return UUID.fromString(account);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
