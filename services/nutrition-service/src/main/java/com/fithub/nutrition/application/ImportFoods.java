package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.food.Food;
import com.fithub.nutrition.domain.food.FoodRepository;
import com.fithub.nutrition.domain.food.InvalidFoodException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Copia a base externa de alimentos (a Tabela TACO) para a base local (DEV-0075, ADR-011). Pode rodar de novo:
 * atualiza pelo id externo (fonte + id), não duplica e não apaga alimento que saiu da base.
 *
 * <p>Item que o domínio recusa (sem energia ou sem um macro, ou com descrição inválida) é pulado, e o id dele entra no
 * resultado para o log.
 */
@Service
public class ImportFoods {

    private final FoodRepository foods;
    private final FoodCatalogSource source;
    private final Clock clock;

    public ImportFoods(FoodRepository foods, FoodCatalogSource source, Clock clock) {
        this.foods = foods;
        this.source = source;
        this.clock = clock;
    }

    /**
     * Contagem de uma execução, para o log.
     *
     * @param skippedIds os ids externos dos itens pulados (dado público da base, sem dado pessoal)
     */
    public record Result(int created, int updated, int unchanged, List<String> skippedIds) {}

    public Result run() {
        int[] counts = new int[3];
        List<String> skipped = new ArrayList<>();
        source.forEach(entry -> {
            Outcome outcome = importOne(entry);
            if (outcome == Outcome.SKIPPED) {
                skipped.add(entry.externalId());
            } else {
                counts[outcome.ordinal()]++;
            }
        });
        return new Result(
                counts[Outcome.CREATED.ordinal()],
                counts[Outcome.UPDATED.ordinal()],
                counts[Outcome.UNCHANGED.ordinal()],
                List.copyOf(skipped));
    }

    private enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED,
        SKIPPED
    }

    private Outcome importOne(FoodCatalogSource.Entry entry) {
        Instant now = clock.instant();
        Optional<Food> existing = foods.findBySource(source.name(), entry.externalId());
        Food candidate;
        try {
            candidate = new Food(
                    existing.map(Food::id).orElseGet(UUID::randomUUID),
                    Food.Kind.TACO,
                    null,
                    entry.name(),
                    entry.baseUnit(),
                    new Food.Nutrients(
                            entry.energyKcal(), entry.proteinG(), entry.carbsG(), entry.fatG(), entry.fiberG()),
                    entry.vegan(),
                    new Food.Source(source.name(), entry.externalId()),
                    existing.map(Food::createdAt).orElse(now),
                    existing.map(Food::updatedAt).orElse(now));
        } catch (InvalidFoodException invalid) {
            return Outcome.SKIPPED;
        }
        if (existing.isEmpty()) {
            foods.save(candidate);
            return Outcome.CREATED;
        }
        // Mesmo conteúdo (com as datas de antes): nada muda, nem a data de atualização.
        if (candidate.equals(existing.get())) {
            return Outcome.UNCHANGED;
        }
        foods.save(new Food(
                candidate.id(),
                candidate.kind(),
                candidate.ownerId(),
                candidate.name(),
                candidate.baseUnit(),
                candidate.per100(),
                candidate.vegan(),
                candidate.source(),
                candidate.createdAt(),
                now));
        return Outcome.UPDATED;
    }
}
