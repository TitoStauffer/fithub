package com.fithub.nutrition.application;

import com.fithub.commons.ids.UuidV7;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodNotFoundException;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.food.InvalidFoodException;
import com.fithub.nutrition.domain.food.Recipe;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Alimentos e receitas próprios da nutricionista (DEV-0077, FR-36): só a dona lê, edita e arquiva (BR-13). Na receita,
 * os valores por 100 g e a etiqueta vegano saem dos ingredientes, calculados ao salvar.
 */
@Service
public class ManageOwnFoods {

    private final FoodRepository foods;
    private final Clock clock;

    public ManageOwnFoods(FoodRepository foods, Clock clock) {
        this.foods = foods;
        this.clock = clock;
    }

    /** O que a nutricionista informa; na receita, os valores e a etiqueta vegano informados são ignorados. */
    public record Draft(
            String name,
            Food.BaseUnit baseUnit,
            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatG,
            BigDecimal fiberG,
            Boolean vegan,
            RecipeDraft recipe) {}

    /** Ingredientes (alimento e quantidade) e o rendimento total, em gramas. */
    public record RecipeDraft(List<Recipe.Ingredient> ingredients, BigDecimal yieldGrams) {}

    public Food create(String nutritionistAccount, Draft draft) {
        UUID ownerId = ViewFood.accountId(nutritionistAccount);
        if (ownerId == null) {
            throw new InvalidFoodException("ownerId", "A conta não tem um identificador válido.");
        }
        Instant now = clock.instant();
        Food food = build(UuidV7.next(clock), ownerId, draft, now, now, null);
        foods.save(food);
        return food;
    }

    /** Edita o próprio (inclusive a receita, que é recalculada); o de outra, ou da TACO, responde 404. */
    public Food update(String nutritionistAccount, UUID foodId, Draft draft) {
        Food current = owned(nutritionistAccount, foodId);
        // Receita não entra como ingrediente: o alimento que já é ingrediente de uma receita não vira receita.
        if (draft.recipe() != null && !current.isRecipe() && foods.usedAsIngredient(current.id())) {
            throw new InvalidFoodException(
                    "recipe", "Este alimento é ingrediente de uma receita e não pode virar receita.");
        }
        Food food = build(
                current.id(), current.ownerId(), draft, current.createdAt(), clock.instant(), current.archivedAt());
        foods.save(food);
        return food;
    }

    /** Arquiva o próprio: sai da busca, mas continua nos planos que o usam. */
    public Food archive(String nutritionistAccount, UUID foodId) {
        Food archived = owned(nutritionistAccount, foodId).archive(clock.instant());
        foods.save(archived);
        return archived;
    }

    private Food owned(String nutritionistAccount, UUID foodId) {
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        return foods.findById(foodId)
                .filter(food -> food.ownedBy(nutritionistId))
                .orElseThrow(FoodNotFoundException::new);
    }

    private Food build(UUID id, UUID ownerId, Draft draft, Instant createdAt, Instant updatedAt, Instant archivedAt) {
        if (draft.recipe() == null) {
            return new Food(
                    id,
                    Food.Kind.OWN,
                    ownerId,
                    draft.name(),
                    draft.baseUnit(),
                    new Food.Nutrients(
                            draft.energyKcal(), draft.proteinG(), draft.carbsG(), draft.fatG(), draft.fiberG()),
                    Boolean.TRUE.equals(draft.vegan()),
                    null,
                    createdAt,
                    updatedAt,
                    null,
                    archivedAt);
        }
        Recipe recipe = new Recipe(draft.recipe().ingredients(), draft.recipe().yieldGrams());
        Map<UUID, Food> ingredients = ingredientsOf(recipe, ownerId, id);
        return new Food(
                id,
                Food.Kind.OWN,
                ownerId,
                draft.name(),
                draft.baseUnit() == null ? Food.BaseUnit.G : draft.baseUnit(),
                recipe.per100(ingredients),
                recipe.vegan(ingredients),
                null,
                createdAt,
                updatedAt,
                recipe,
                archivedAt);
    }

    /**
     * Os alimentos dos ingredientes: cada um precisa existir, ser visível para a dona (TACO ou próprio dela) e não ser
     * outra receita (nem a própria).
     */
    private Map<UUID, Food> ingredientsOf(Recipe recipe, UUID ownerId, UUID recipeId) {
        Map<UUID, Food> found = new HashMap<>();
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            UUID foodId = recipe.ingredients().get(index).foodId();
            String field = "recipe.ingredients[" + index + "].foodId";
            Food ingredient = foods.findById(foodId)
                    .filter(food -> food.visibleTo(ownerId))
                    .filter(food -> !food.isRecipe() && !food.id().equals(recipeId))
                    .orElseThrow(() -> new InvalidFoodException(
                            field,
                            "Ingrediente indisponível: ele não existe, é de outra nutricionista ou é uma receita."));
            found.put(foodId, ingredient);
        }
        return found;
    }
}
