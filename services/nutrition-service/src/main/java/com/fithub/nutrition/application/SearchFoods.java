package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.food.FoodSearch;
import org.springframework.stereotype.Service;

/** Busca de alimentos pela nutricionista (DEV-0078, FR-35, FR-38): a TACO e os próprios dela (BR-13). */
@Service
public class SearchFoods {

    private final FoodRepository foods;

    public SearchFoods(FoodRepository foods) {
        this.foods = foods;
    }

    /**
     * @param nutritionistAccount o {@code sub} do token (ADR-009); conta sem {@code sub} em UUID vê só a TACO
     */
    public FoodSearch.Page search(String nutritionistAccount, FoodSearch search) {
        return foods.search(search, ViewFood.accountId(nutritionistAccount));
    }
}
