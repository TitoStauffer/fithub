package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.LinkProjection;
import com.fithub.nutrition.domain.access.ProfessionalAccessAudit;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.food.Recipe;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.restriction.FoodRestriction;
import com.fithub.nutrition.domain.restriction.FoodRestrictionRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Alertas de restrição alimentar no plano (DEV-0084, FR-41, BR-11): cada alimento (principal ou alternativa) cujo nome,
 * ou o nome de um ingrediente da receita, tem um termo de alergia ou intolerância do paciente gera um alerta. Nunca
 * bloqueia salvar nem publicar.
 *
 * <p>O alerta é calculado na hora, com as restrições atuais do paciente, e só aparece com vínculo ativo: depois que o
 * vínculo acaba, a autora ainda lê o plano (BR-05), mas não as restrições do paciente.
 */
@Service
public class MealPlanAlerts {

    /** Sem alerta: o vínculo acabou, o paciente não tem restrição ou quem lê é o próprio paciente. */
    static final Function<Food, List<FoodRestriction>> NONE = food -> List.of();

    private final LinkProjection links;
    private final FoodRestrictionRepository restrictions;
    private final FoodRepository foods;
    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public MealPlanAlerts(
            LinkProjection links,
            FoodRestrictionRepository restrictions,
            FoodRepository foods,
            ProfessionalAccessAudit audit,
            Clock clock) {
        this.links = links;
        this.restrictions = restrictions;
        this.foods = foods;
        this.audit = audit;
        this.clock = clock;
    }

    /** As restrições que cada alimento do plano dispara, para a autora com vínculo ativo. */
    public Function<Food, List<FoodRestriction>> of(MealPlan plan) {
        if (!links.hasActiveLink(plan.nutritionistId(), plan.clientId())) {
            return NONE;
        }
        List<FoodRestriction> restricted = restrictions.findByClient(plan.clientId());
        if (restricted.isEmpty()) {
            return NONE;
        }
        // O alerta mostra a descrição da restrição à nutricionista: é leitura de dado de saúde (DEV-0171, NFR-03).
        // Registra quando as restrições entram na resposta, mesmo que nenhum alimento as dispare: a mais, nunca a
        // menos.
        audit.record(
                plan.nutritionistId(),
                plan.clientId(),
                ProfessionalAccessAudit.Resource.FOOD_RESTRICTIONS,
                clock.instant());
        Map<UUID, Optional<Food>> cache = new HashMap<>();
        return food -> {
            List<String> names = names(food, cache);
            return restricted.stream()
                    .filter(restriction -> names.stream().anyMatch(restriction::alertsFor))
                    .toList();
        };
    }

    /** O nome do alimento e, na receita, o de cada ingrediente. */
    private List<String> names(Food food, Map<UUID, Optional<Food>> cache) {
        List<String> names = new ArrayList<>();
        names.add(food.name());
        if (food.isRecipe()) {
            for (Recipe.Ingredient ingredient : food.recipe().ingredients()) {
                cache.computeIfAbsent(ingredient.foodId(), foods::findById).ifPresent(found -> names.add(found.name()));
            }
        }
        return names;
    }
}
