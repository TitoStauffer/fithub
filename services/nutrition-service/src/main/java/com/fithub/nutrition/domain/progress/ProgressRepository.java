package com.fithub.nutrition.domain.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consultas de evolução da dieta (DEV-0092), calculadas na leitura dos planos e dos registros. */
public interface ProgressRepository {

    /**
     * Os planos já publicados do paciente, com as versões publicadas.
     *
     * @param nutritionistId só os planos desta nutricionista; vazio para todos
     */
    List<PublishedPlan> publishedPlans(UUID clientId, UUID nutritionistId);

    /** Os registros do paciente no intervalo de dias, nos planos do filtro. */
    List<LoggedMeal> logs(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to);

    /** As alternativas mais escolhidas no intervalo: o alimento e quantas vezes, da mais usada para a menos. */
    List<AlternativeUse> topAlternatives(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to, int limit);

    /** Os registros do intervalo com desvio ou foto, do mais novo para o mais antigo. */
    List<Deviation> deviations(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to);

    /** O último registro de refeição de cada paciente nos planos da nutricionista; quem nunca registrou fica de fora. */
    Map<UUID, Instant> lastLogged(UUID nutritionistId, Collection<UUID> clientIds);

    /**
     * Um plano publicado.
     *
     * @param replacedAt quando foi substituído; vazio enquanto continua valendo
     * @param versions quando cada versão foi publicada e quantas refeições tem, da primeira para a última
     */
    record PublishedPlan(UUID planId, Instant replacedAt, List<PublishedVersion> versions) {}

    record PublishedVersion(Instant publishedAt, int meals) {}

    record LoggedMeal(LocalDate day, UUID planId, String status) {}

    record AlternativeUse(UUID foodId, String foodName, long times) {}

    /**
     * Um desvio registrado.
     *
     * @param photoKey a chave da foto do prato no bucket privado, se houver
     */
    record Deviation(UUID logId, LocalDate day, int mealPosition, String mealName, String deviation, String photoKey) {}
}
