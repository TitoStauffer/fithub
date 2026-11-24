package com.fithub.training.application;

import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.plan.PlanRepository;
import com.fithub.training.domain.record.InvalidRecordException;
import com.fithub.training.domain.record.ManualRecord;
import com.fithub.training.domain.record.PersonalRecords;
import com.fithub.training.domain.record.RecordNotFoundException;
import com.fithub.training.domain.record.RecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Recordes pessoais (DEV-0068, FR-53 a FR-55): calculados na leitura, das séries registradas e dos lançamentos manuais
 * do aluno. O aluno lê e lança os dele; o personal com vínculo ativo lê os do aluno (BR-02). A nutricionista não tem
 * rota (BR-03).
 */
@Service
public class ClientRecords {

    private final RecordRepository records;
    private final ExerciseRepository exercises;
    private final PlanRepository plans;
    private final ClientAccess clientAccess;
    private final Clock clock;

    public ClientRecords(
            RecordRepository records,
            ExerciseRepository exercises,
            PlanRepository plans,
            ClientAccess clientAccess,
            Clock clock) {
        this.records = records;
        this.exercises = exercises;
        this.plans = plans;
        this.clientAccess = clientAccess;
        this.clock = clock;
    }

    /** Os recordes por exercício, com os exercícios para a tela mostrar o nome. */
    public record Records(List<PersonalRecords.ExerciseRecords> byExercise, Map<UUID, Exercise> exercises) {}

    /** O que o aluno lança à mão. */
    public record ManualDraft(UUID exerciseId, BigDecimal loadKg, Integer reps, LocalDate performedOn) {}

    public Records mine(String clientAccount) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        return clientId == null ? new Records(List.of(), Map.of()) : recordsOf(clientId);
    }

    /** Os recordes do aluno vinculado, para o personal (FR-55). Sem vínculo ativo, 404. */
    public Records ofClient(String personalAccount, UUID clientId) {
        clientAccess.requireActiveLink(personalAccount, clientId);
        return recordsOf(clientId);
    }

    /**
     * Lança um recorde manual. O exercício precisa ser do catálogo ou estar numa ficha que o aluno recebeu.
     */
    public ManualRecord addManual(String clientAccount, ManualDraft draft) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        if (clientId == null) {
            throw new InvalidRecordException("exerciseId", "A conta não tem um identificador válido.");
        }
        if (draft.exerciseId() != null && !usableBy(clientId, draft.exerciseId())) {
            throw new InvalidRecordException(
                    "exerciseId", "Use um exercício do catálogo ou de uma ficha que você recebeu.");
        }
        ManualRecord record = ManualRecord.of(
                clientId,
                draft.exerciseId(),
                draft.loadKg(),
                draft.reps(),
                draft.performedOn(),
                LocalDate.now(clock.withZone(ManagePlans.ZONE)),
                clock.instant());
        records.saveManual(record);
        return record;
    }

    /** Apaga um lançamento manual do próprio aluno. */
    public void removeManual(String clientAccount, UUID recordId) {
        UUID clientId = ViewExercise.accountId(clientAccount);
        ManualRecord record = records.findManual(recordId)
                .filter(found -> found.ownedBy(clientId))
                .orElseThrow(RecordNotFoundException::new);
        records.deleteManual(record.id());
    }

    private Records recordsOf(UUID clientId) {
        List<PersonalRecords.ExerciseRecords> byExercise = PersonalRecords.of(records.performancesOf(clientId));
        Map<UUID, Exercise> found = new HashMap<>();
        byExercise.forEach(entry ->
                exercises.findById(entry.exerciseId()).ifPresent(exercise -> found.put(entry.exerciseId(), exercise)));
        return new Records(byExercise, found);
    }

    private boolean usableBy(UUID clientId, UUID exerciseId) {
        boolean catalog = exercises
                .findById(exerciseId)
                .map(exercise -> exercise.kind() == Exercise.Kind.CATALOG)
                .orElse(false);
        return catalog
                || plans.findPublishedForClient(clientId).stream()
                        .anyMatch(plan -> plan.current().exerciseIds().contains(exerciseId));
    }
}
