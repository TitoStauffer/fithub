package com.fithub.training.application;

import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.InvalidExerciseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Copia a base externa para o catálogo local (DEV-0054, ADR-010). Pode rodar de novo: atualiza pelo id externo
 * (fonte + id), não duplica e não apaga exercício que saiu da base.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: item que o domínio recusa (sem nome, sem grupo muscular
 * mapeável, sem licença ou autor do texto) é pulado e só contado; a mídia entra só com licença Creative Commons e
 * autor registrados nela, e o crédito dela fica junto da atribuição.
 */
@Service
public class ImportExercises {

    private final ExerciseRepository exercises;
    private final ExerciseCatalogSource source;
    private final Clock clock;

    public ImportExercises(ExerciseRepository exercises, ExerciseCatalogSource source, Clock clock) {
        this.exercises = exercises;
        this.source = source;
        this.clock = clock;
    }

    /** Contagem de uma execução, para o log (sem dado pessoal). */
    public record Result(int created, int updated, int unchanged, int skipped) {}

    /**
     * Importa a base inteira.
     *
     * @throws ExerciseSourceUnavailableException quando a base falha; o que foi gravado antes continua valendo
     */
    public Result run() {
        int[] counts = new int[Outcome.values().length];
        source.forEach(entry -> counts[importOne(entry).ordinal()]++);
        return new Result(
                counts[Outcome.CREATED.ordinal()],
                counts[Outcome.UPDATED.ordinal()],
                counts[Outcome.UNCHANGED.ordinal()],
                counts[Outcome.SKIPPED.ordinal()]);
    }

    private enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED,
        SKIPPED
    }

    private Outcome importOne(ExerciseCatalogSource.Entry entry) {
        Instant now = clock.instant();
        Optional<Exercise> existing = entry.externalId() == null
                ? Optional.empty()
                : exercises.findByExternalId(source.name(), entry.externalId());
        Exercise candidate;
        try {
            ExerciseCatalogSource.Media media = licensedMedia(entry);
            candidate = new Exercise(
                    existing.map(Exercise::id).orElseGet(UUID::randomUUID),
                    Exercise.Kind.CATALOG,
                    null,
                    entry.name(),
                    entry.muscleGroups(),
                    entry.equipment(),
                    entry.instructions(),
                    media == null ? null : media.url(),
                    new Attribution(
                            source.name(),
                            entry.externalId(),
                            entry.license(),
                            entry.author(),
                            media == null ? null : media.license(),
                            media == null ? null : media.author()),
                    existing.map(Exercise::createdAt).orElse(now),
                    existing.map(Exercise::updatedAt).orElse(now));
        } catch (InvalidExerciseException invalid) {
            return Outcome.SKIPPED;
        }
        if (existing.isEmpty()) {
            exercises.save(candidate);
            return Outcome.CREATED;
        }
        // Mesmo conteúdo (com as datas de antes): nada muda, nem a data de atualização.
        if (candidate.equals(existing.get())) {
            return Outcome.UNCHANGED;
        }
        exercises.save(withUpdatedAt(candidate, now));
        return Outcome.UPDATED;
    }

    /**
     * Primeira mídia com licença Creative Commons e autor registrados nela, e com um link que o domínio aceita
     * (ADR-010). Sem nenhuma assim, o exercício entra sem mídia.
     */
    static ExerciseCatalogSource.Media licensedMedia(ExerciseCatalogSource.Entry entry) {
        if (entry.media() == null) {
            return null;
        }
        return entry.media().stream()
                .filter(media -> media != null
                        && isCreativeCommons(media.license())
                        && media.author() != null
                        && !media.author().isBlank()
                        && Exercise.acceptsMediaUrl(media.url()))
                .findFirst()
                .orElse(null);
    }

    /** CC-BY, CC-BY-SA, CC0... Outras licenças (por exemplo ODbL, que é de base de dados) não valem para mídia. */
    private static boolean isCreativeCommons(String license) {
        return license != null && license.strip().startsWith("CC");
    }

    private static Exercise withUpdatedAt(Exercise exercise, Instant updatedAt) {
        return new Exercise(
                exercise.id(),
                exercise.kind(),
                exercise.ownerId(),
                exercise.name(),
                exercise.muscleGroups(),
                exercise.equipment(),
                exercise.instructions(),
                exercise.mediaUrl(),
                exercise.attribution(),
                exercise.createdAt(),
                updatedAt);
    }
}
