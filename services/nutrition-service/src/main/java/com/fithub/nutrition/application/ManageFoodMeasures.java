package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodMeasure;
import com.fithub.nutrition.domain.food.FoodMeasureNotFoundException;
import com.fithub.nutrition.domain.food.FoodMeasureRepository;
import com.fithub.nutrition.domain.food.InvalidFoodException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Medidas caseiras da nutricionista (DEV-0076, FR-37): em qualquer alimento que ela vê (TACO ou próprio), só dela
 * (BR-13), até 10 por alimento e sem nome repetido.
 */
@Service
public class ManageFoodMeasures {

    private final ViewFood viewFood;
    private final FoodMeasureRepository measures;
    private final Clock clock;

    public ManageFoodMeasures(ViewFood viewFood, FoodMeasureRepository measures, Clock clock) {
        this.viewFood = viewFood;
        this.measures = measures;
        this.clock = clock;
    }

    /** As medidas da nutricionista no alimento; alimento que ela não vê responde 404. */
    public List<FoodMeasure> list(String nutritionistAccount, UUID foodId) {
        Food food = viewFood.view(nutritionistAccount, foodId);
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        return nutritionistId == null ? List.of() : measures.findFor(food.id(), nutritionistId);
    }

    public FoodMeasure add(String nutritionistAccount, UUID foodId, String name, BigDecimal amount) {
        Food food = viewFood.view(nutritionistAccount, foodId);
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        if (nutritionistId == null) {
            throw new InvalidFoodException("ownerId", "A conta não tem um identificador válido.");
        }
        FoodMeasure measure =
                new FoodMeasure(UUID.randomUUID(), food.id(), nutritionistId, name, amount, clock.instant());
        List<FoodMeasure> existing = measures.findFor(food.id(), nutritionistId);
        if (existing.size() >= FoodMeasure.MAX_PER_FOOD) {
            throw new InvalidFoodException("measures", "Cada alimento tem até 10 medidas suas.");
        }
        try {
            measures.save(measure);
        } catch (DataIntegrityViolationException sameName) {
            // Nome repetido (sem diferenciar maiúsculas), mesmo em gravações simultâneas: o índice único decide.
            throw new InvalidFoodException("name", "Já existe uma medida com esse nome neste alimento.");
        }
        return measure;
    }

    /** Apaga uma medida da nutricionista; a de outra, ou de outro alimento, responde 404. */
    public void remove(String nutritionistAccount, UUID foodId, UUID measureId) {
        UUID nutritionistId = ViewFood.accountId(nutritionistAccount);
        FoodMeasure measure = measures.findById(measureId)
                .filter(found -> found.foodId().equals(foodId))
                .filter(found -> found.ownedBy(nutritionistId))
                .orElseThrow(FoodMeasureNotFoundException::new);
        measures.delete(measure.id());
    }
}
