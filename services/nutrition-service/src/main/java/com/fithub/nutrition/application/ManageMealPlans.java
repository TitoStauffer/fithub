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
import com.fithub.nutrition.domain.restriction.FoodRestriction;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final MealPlanAlerts alerts;
    private final Clock clock;
    private final PrescriptionAccess prescriptionAccess;

    public ManageMealPlans(
            ClientAccess clientAccess,
            MealPlanRepository plans,
            FoodRepository foods,
            FoodMeasureRepository measures,
            MealPlanAlerts alerts,
            Clock clock,
            PrescriptionAccess prescriptionAccess) {
        this.prescriptionAccess = prescriptionAccess;
        this.clientAccess = clientAccess;
        this.plans = plans;
        this.foods = foods;
        this.measures = measures;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** O que a nutricionista informa para o plano. */
    public record Draft(Integer baseVersion, String name, LocalDate startsOn, String note, List<MealDraft> meals) {}

    /** Uma refeição como chega; os alimentos ainda sem a quantidade resolvida. */
    public record MealDraft(String name, LocalTime suggestedTime, String note, List<ItemDraft> items) {}

    /**
     * Um alimento como chega: a quantidade direta, ou a medida caseira dela e quantas (a quantidade vem da medida).
     */
    public record ItemDraft(
            UUID foodId,
            BigDecimal amount,
            UUID measureId,
            BigDecimal measureCount,
            List<AlternativeDraft> alternatives) {}

    /** Uma alternativa como chega (DEV-0082): o alimento e a quantidade dele. */
    public record AlternativeDraft(UUID foodId, BigDecimal amount) {}

    /** O plano com os alimentos e os macros de cada item, de cada refeição e do dia. */
    public record PlanView(MealPlan plan, MealPlanVersion version, List<MealView> meals, Food.Amounts day) {}

    public record MealView(Meal meal, List<ItemView> items, Food.Amounts total) {}

    /** O item com o alimento (nome, unidade), a medida usada para exibir (se houver) e os macros da quantidade. */
    public record ItemView(
            MealItem item,
            Food food,
            FoodMeasure measure,
            Food.Amounts nutrients,
            List<AlternativeView> alternatives,
            List<FoodRestriction> alerts) {}

    /**
     * A alternativa com o alimento, os macros da quantidade dela e a diferença em relação ao principal (alternativa
     * menos principal, com sinal).
     */
    public record AlternativeView(
            MealItem.Alternative alternative,
            Food food,
            Food.Amounts nutrients,
            Food.Amounts difference,
            List<FoodRestriction> alerts) {}

    public PlanView create(String nutritionistAccount, UUID clientId, Draft draft) {
        clientAccess.requireActiveLink(nutritionistAccount, clientId);
        // Sem assinatura válida, não cria prescrição; o que já entregou continua (FR-06, DEV-0101).
        prescriptionAccess.requireSubscription(nutritionistAccount);
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
     * Edita o plano (DEV-0083, BR-09): o rascunho no lugar; o publicado vira a versão seguinte, já publicada.
     *
     * @throws MealPlanStateException se o plano foi substituído, outra edição chegou antes ou ele mudou desde a leitura
     */
    public PlanView update(String nutritionistAccount, UUID planId, Draft draft) {
        MealPlan plan = authored(nutritionistAccount, planId);
        clientAccess.requireActiveLink(nutritionistAccount, plan.clientId());
        prescriptionAccess.requireSubscription(nutritionistAccount);
        MealPlan edited = plan.edit(
                draft.baseVersion(),
                UuidV7.next(clock),
                draft.name(),
                draft.startsOn(),
                draft.note(),
                resolveMeals(draft.meals(), plan.nutritionistId(), foodsIn(plan)),
                clock.instant());
        if (edited.current().number() == plan.current().number()) {
            plans.saveDraft(edited, plan.updatedAt());
        } else {
            plans.addVersion(edited, plan.updatedAt());
        }
        return view(edited);
    }

    /** As versões do plano, da primeira para a última, para a autora. */
    public List<MealPlanRepository.VersionSummary> versions(String nutritionistAccount, UUID planId) {
        return plans.findVersions(authored(nutritionistAccount, planId).id());
    }

    /** Uma versão do plano, com os macros, para a autora. */
    public PlanView version(String nutritionistAccount, UUID planId, int number) {
        MealPlan plan = authored(nutritionistAccount, planId);
        return view(
                plan,
                plans.findVersion(plan.id(), number).orElseThrow(MealPlanNotFoundException::new),
                alerts.of(plan));
    }

    /**
     * As versões do plano que o paciente recebeu (DEV-0083). Só o plano que já foi publicado aparece, e nele toda versão
     * é publicada: editar o publicado gera a versão nova já publicada, e o rascunho é só a versão 1 do plano em
     * rascunho.
     *
     * @param clientAccount o {@code sub} do token, que é o id do paciente (ADR-009)
     */
    public List<MealPlanRepository.VersionSummary> clientVersions(String clientAccount, UUID planId) {
        return plans.findVersions(received(clientAccount, planId).id());
    }

    /** Uma versão do plano que o paciente recebeu, com os macros; o alerta é para a nutricionista, não vem aqui. */
    public PlanView clientVersion(String clientAccount, UUID planId, int number) {
        MealPlan plan = received(clientAccount, planId);
        return view(
                plan,
                plans.findVersion(plan.id(), number).orElseThrow(MealPlanNotFoundException::new),
                MealPlanAlerts.NONE);
    }

    /** O plano do paciente, já publicado alguma vez; o de outro paciente e o rascunho respondem como inexistentes. */
    private MealPlan received(String clientAccount, UUID planId) {
        UUID clientId = ViewFood.accountId(clientAccount);
        return plans.findById(planId)
                .filter(plan -> plan.clientId().equals(clientId))
                .filter(plan -> plan.status() != MealPlan.Status.DRAFT)
                .orElseThrow(MealPlanNotFoundException::new);
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
        prescriptionAccess.requireSubscription(nutritionistAccount);
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
        requireUsableFood(draft.foodId(), nutritionistId, alreadyInPlan, "foodId");
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
        // O domínio confere o limite e a repetição das alternativas; depois, cada uma precisa ser um alimento usável.
        MealItem item = new MealItem(
                draft.foodId(), amount, draft.measureId(), draft.measureCount(), alternatives(draft.alternatives()));
        for (int index = 0; index < item.alternatives().size(); index++) {
            requireUsableFood(
                    item.alternatives().get(index).foodId(),
                    nutritionistId,
                    alreadyInPlan,
                    "alternatives[" + index + "].foodId");
        }
        return item;
    }

    private static List<MealItem.Alternative> alternatives(List<AlternativeDraft> drafts) {
        if (drafts == null) {
            return List.of();
        }
        List<MealItem.Alternative> alternatives = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            AlternativeDraft draft = drafts.get(index);
            try {
                alternatives.add(draft == null ? null : new MealItem.Alternative(draft.foodId(), draft.amount()));
            } catch (InvalidMealPlanException invalid) {
                throw invalid.under("alternatives[" + index + "]");
            }
        }
        return alternatives;
    }

    /** O alimento precisa ser da TACO ou da autora e não estar arquivado, a menos que já estivesse no plano. */
    private void requireUsableFood(UUID foodId, UUID nutritionistId, Set<UUID> alreadyInPlan, String field) {
        boolean usable = foods.findById(foodId)
                .filter(food -> food.visibleTo(nutritionistId))
                .filter(food -> !food.archived() || alreadyInPlan.contains(food.id()))
                .isPresent();
        if (!usable) {
            throw new InvalidMealPlanException(
                    field, "Alimento indisponível: ele não existe, foi arquivado ou é de outra nutricionista.");
        }
    }

    /** Os macros do plano: cada item pela quantidade, a refeição pela soma dos itens e o dia pela soma das refeições. */
    /** A versão atual do plano para o paciente (DEV-0087): os macros sem os alertas, que são da nutricionista. */
    PlanView clientView(MealPlan plan) {
        return view(plan, plan.current(), MealPlanAlerts.NONE);
    }

    /** A versão atual, com os alertas para a autora com vínculo ativo (DEV-0084). */
    PlanView view(MealPlan plan) {
        return view(plan, plan.current(), alerts.of(plan));
    }

    private PlanView view(MealPlan plan, MealPlanVersion version, Function<Food, List<FoodRestriction>> alertsFor) {
        Map<UUID, Food> foodCache = new HashMap<>();
        Map<UUID, Optional<FoodMeasure>> measureCache = new HashMap<>();
        List<MealView> meals = new ArrayList<>();
        Food.Amounts day = Food.Amounts.ZERO;
        for (Meal meal : version.meals()) {
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
                List<AlternativeView> alternatives = new ArrayList<>();
                for (MealItem.Alternative alternative : item.alternatives()) {
                    Food other = foodCache.computeIfAbsent(
                            alternative.foodId(), id -> foods.findById(id).orElseThrow());
                    Food.Amounts otherNutrients = other.nutrientsIn(alternative.amount());
                    alternatives.add(new AlternativeView(
                            alternative,
                            other,
                            otherNutrients,
                            otherNutrients.minus(nutrients),
                            alertsFor.apply(other)));
                }
                // Só o principal entra na soma da refeição: a alternativa é uma troca, não um acréscimo.
                items.add(new ItemView(item, food, measure, nutrients, alternatives, alertsFor.apply(food)));
                total = total.plus(nutrients);
            }
            meals.add(new MealView(meal, items, total));
            day = day.plus(total);
        }
        return new PlanView(plan, version, meals, day);
    }

    private static Set<UUID> foodsIn(MealPlan plan) {
        return plan.current().meals().stream()
                .flatMap(meal -> meal.items().stream())
                .flatMap(item -> Stream.concat(
                        Stream.of(item.foodId()), item.alternatives().stream().map(MealItem.Alternative::foodId)))
                .collect(Collectors.toSet());
    }

    private MealPlan authored(String nutritionistAccount, UUID planId) {
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        return plans.findById(planId)
                .filter(plan -> plan.authoredBy(nutritionistId))
                .orElseThrow(MealPlanNotFoundException::new);
    }
}
