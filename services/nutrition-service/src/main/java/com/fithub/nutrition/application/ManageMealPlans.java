package com.fithub.nutrition.application;

import com.fithub.commons.ids.UuidV7;
import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodMeasure;
import com.fithub.nutrition.domain.food.FoodMeasureRepository;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.plan.InvalidMealPlanException;
import com.fithub.nutrition.domain.plan.Meal;
import com.fithub.nutrition.domain.plan.MealItem;
import com.fithub.nutrition.domain.plan.MealPlan;
import com.fithub.nutrition.domain.plan.MealPlanNotFoundException;
import com.fithub.nutrition.domain.plan.MealPlanRepository;
import com.fithub.nutrition.domain.plan.MealPlanStateException;
import com.fithub.nutrition.domain.plan.MealPlanVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planos alimentares da nutricionista (DEV-0081, FR-42, FR-43, FR-45). Criar, listar, editar e publicar exigem vínculo
 * ativo com o paciente (BR-02); a autora continua lendo o plano que criou depois que o vínculo acaba (BR-05). Outra
 * nutricionista nunca vê o plano. Os macros são calculados na leitura, por item, refeição e dia.
 */
@Service
public class ManageMealPlans {

    private final ClientAccess clientAccess;
    private final MealPlanRepository plans;
    private final FoodRepository foods;
    private final FoodMeasureRepository measures;
    private final Clock clock;

    public ManageMealPlans(
            ClientAccess clientAccess,
            MealPlanRepository plans,
            FoodRepository foods,
            FoodMeasureRepository measures,
            Clock clock) {
        this.clientAccess = clientAccess;
        this.plans = plans;
        this.foods = foods;
        this.measures = measures;
        this.clock = clock;
    }

    /** O que a nutricionista informa para o plano. */
    public record Draft(String name, LocalDate startsOn, String note, List<MealDraft> meals) {}

    /** Uma refeição como chega; os alimentos ainda sem a quantidade resolvida. */
    public record MealDraft(String name, LocalTime suggestedTime, String note, List<ItemDraft> items) {}

    /**
     * Um alimento como chega: a quantidade direta, ou a medida caseira dela e quantas (a quantidade vem da medida).
     */
    public record ItemDraft(UUID foodId, BigDecimal amount, UUID measureId, BigDecimal measureCount) {}

    /** O plano com os alimentos e os macros de cada item, de cada refeição e do dia. */
    public record PlanView(MealPlan plan, List<MealView> meals, Food.Amounts day) {}

    public record MealView(Meal meal, List<ItemView> items, Food.Amounts total) {}

    /** O item com o alimento (nome, unidade), a medida usada para exibir (se houver) e os macros da quantidade. */
    public record ItemView(MealItem item, Food food, FoodMeasure measure, Food.Amounts nutrients) {}

    public PlanView create(String nutritionistAccount, UUID clientId, Draft draft) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        UUID nutritionistId = UUID.fromString(nutritionistAccount);
        MealPlan plan = MealPlan.newDraft(
                UuidV7.next(clock),
                UuidV7.next(clock),
                clientId,
                nutritionistId,
                draft.name(),
                draft.startsOn(),
                draft.note(),
                resolveMeals(draft.meals(), nutritionistId, Set.of()),
                clock.instant());
        plans.add(plan);
        return view(plan);
    }

    /** Os planos que a nutricionista criou para o paciente vinculado, do mais novo para o mais antigo. */
    public List<PlanView> listForClient(String nutritionistAccount, UUID clientId) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        return plans.findByClientAndNutritionist(clientId, UUID.fromString(nutritionistAccount)).stream()
                .map(this::view)
                .toList();
    }

    /** O plano, só para a autora; com ou sem vínculo ativo (BR-05). */
    public PlanView view(String nutritionistAccount, UUID planId) {
        return view(authored(nutritionistAccount, planId));
    }

    /**
     * Edita o rascunho no lugar.
     *
     * @throws MealPlanStateException se o plano já foi publicado (a nova versão vem na DEV-0083) ou mudou desde a
     *     leitura
     */
    public PlanView update(String nutritionistAccount, UUID planId, Draft draft) {
        MealPlan plan = authored(nutritionistAccount, planId);
        clientAccess.requireActiveLink(nutritionistAccount, plan.clientId());
        MealPlan edited = plan.editDraft(
                draft.name(),
                draft.startsOn(),
                draft.note(),
                resolveMeals(draft.meals(), plan.nutritionistId(), foodsIn(plan)),
                clock.instant());
        plans.saveDraft(edited, plan.updatedAt());
        return view(edited);
    }

    /**
     * Publica o rascunho; o plano que estava ativo para o paciente fica substituído (um ativo por vez).
     *
     * @throws InvalidMealPlanException se alguma refeição não tem alimento (BR-10)
     */
    @Transactional
    public PlanView publish(String nutritionistAccount, UUID planId) {
        MealPlan plan = authored(nutritionistAccount, planId);
        clientAccess.requireActiveLink(nutritionistAccount, plan.clientId());
        MealPlan published = plan.publish(clock.instant());
        plans.findPublished(plan.clientId())
                .ifPresent(active -> plans.saveStatus(active.replace(clock.instant()), active.updatedAt()));
        try {
            plans.saveStatus(published, plan.updatedAt());
        } catch (DataIntegrityViolationException otherPublished) {
            // Duas publicações para o mesmo paciente ao mesmo tempo: a segunda bate no índice de um ativo por vez.
            throw new MealPlanStateException("Outro plano foi publicado ao mesmo tempo. Tente de novo.");
        }
        return view(published);
    }

    /**
     * As refeições com os alimentos resolvidos. Cada alimento precisa ser da TACO ou próprio da autora e não pode estar
     * arquivado, a menos que já estivesse no plano. A medida caseira precisa ser da autora e daquele alimento.
     */
    List<Meal.Draft> resolveMeals(List<MealDraft> drafts, UUID nutritionistId, Set<UUID> alreadyInPlan) {
        MealPlanVersion.requireMealCount(drafts);
        List<Meal.Draft> meals = new ArrayList<>();
        for (int meal = 0; meal < drafts.size(); meal++) {
            MealDraft draft = drafts.get(meal);
            if (draft == null) {
                throw new InvalidMealPlanException("meals[" + meal + "]", "Informe a refeição.");
            }
            // Mais de 30 itens: a refeição recusa ao montar (Meal), com o caminho dela.
            List<ItemDraft> items = draft.items() == null ? List.of() : draft.items();
            List<MealItem> resolved = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                String path = "meals[" + meal + "].items[" + index + "]";
                try {
                    resolved.add(resolveItem(items.get(index), nutritionistId, alreadyInPlan));
                } catch (InvalidMealPlanException invalid) {
                    throw invalid.under(path);
                }
            }
            meals.add(new Meal.Draft(draft.name(), draft.suggestedTime(), draft.note(), resolved));
        }
        return meals;
    }

    private MealItem resolveItem(ItemDraft draft, UUID nutritionistId, Set<UUID> alreadyInPlan) {
        if (draft == null) {
            throw new InvalidMealPlanException("foodId", "Escolha o alimento.");
        }
        boolean usable = foods.findById(draft.foodId())
                .filter(food -> food.visibleTo(nutritionistId))
                .filter(food -> !food.archived() || alreadyInPlan.contains(food.id()))
                .isPresent();
        if (!usable) {
            throw new InvalidMealPlanException(
                    "foodId", "Alimento indisponível: ele não existe, foi arquivado ou é de outra nutricionista.");
        }
        BigDecimal amount = draft.amount();
        if (draft.measureId() != null) {
            FoodMeasure measure = measures.findById(draft.measureId())
                    .filter(found -> found.ownedBy(nutritionistId))
                    .filter(found -> found.foodId().equals(draft.foodId()))
                    .orElseThrow(() -> new InvalidMealPlanException(
                            "measureId", "Medida indisponível: ela não existe ou não é deste alimento."));
            if (MealItem.validMeasureCount(draft.measureCount())) {
                amount = measure.toBaseUnit(draft.measureCount());
            }
        }
        return new MealItem(draft.foodId(), amount, draft.measureId(), draft.measureCount());
    }

    /** Os macros do plano: cada item pela quantidade, a refeição pela soma dos itens e o dia pela soma das refeições. */
    PlanView view(MealPlan plan) {
        Map<UUID, Food> foodCache = new HashMap<>();
        Map<UUID, Optional<FoodMeasure>> measureCache = new HashMap<>();
        List<MealView> meals = new ArrayList<>();
        Food.Amounts day = Food.Amounts.ZERO;
        for (Meal meal : plan.current().meals()) {
            List<ItemView> items = new ArrayList<>();
            Food.Amounts total = Food.Amounts.ZERO;
            for (MealItem item : meal.items()) {
                Food food = foodCache.computeIfAbsent(
                        item.foodId(), id -> foods.findById(id).orElseThrow());
                FoodMeasure measure = item.measureId() == null
                        ? null
                        : measureCache
                                .computeIfAbsent(item.measureId(), measures::findById)
                                .orElse(null);
                Food.Amounts nutrients = food.nutrientsIn(item.amount());
                items.add(new ItemView(item, food, measure, nutrients));
                total = total.plus(nutrients);
            }
            meals.add(new MealView(meal, items, total));
            day = day.plus(total);
        }
        return new PlanView(plan, meals, day);
    }

    private static Set<UUID> foodsIn(MealPlan plan) {
        return plan.current().meals().stream()
                .flatMap(meal -> meal.items().stream())
                .map(MealItem::foodId)
                .collect(Collectors.toSet());
    }

    private MealPlan authored(String nutritionistAccount, UUID planId) {
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        return plans.findById(planId)
                .filter(plan -> plan.authoredBy(nutritionistId))
                .orElseThrow(MealPlanNotFoundException::new);
    }
}
