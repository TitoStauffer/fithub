package com.fithub.nutrition.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.metrics.AggregateGauges;
import com.fithub.testing.service.ServiceIntegrationTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Métricas de sucesso da nutrição (DEV-0113). Cada caso confere o acréscimo sobre o banco; os dias são os de São
 * Paulo, como o registro de refeição.
 */
class NutritionMetricsTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AggregateGauges gauges;

    @Autowired
    MeterRegistry registry;

    @Test
    void engagementCountsLinkedClientsWithAMealEatenInTheLast7Days() {
        Map<String, Double> after = onOneDay(today -> {
            Map<String, Double> before = values();
            UUID nutritionist = UUID.randomUUID();
            // Sexto dia antes de hoje: ainda dentro da semana (hoje e os 6 anteriores).
            UUID engaged = linked(nutritionist);
            meal(engaged, plan(nutritionist, engaged, null), today.minusDays(6), "PARTIAL");
            UUID stale = linked(nutritionist);
            meal(stale, plan(nutritionist, stale, null), today.minusDays(7), "DONE");
            // Refeição pulada não é registro de refeição feita.
            UUID skipped = linked(nutritionist);
            meal(skipped, plan(nutritionist, skipped, null), today, "SKIPPED");
            // Cliente com vínculo encerrado que comeu nesta semana: fora dos vinculados e do engajamento.
            UUID unlinked = ended(nutritionist, Instant.now().minus(Duration.ofDays(40)));
            meal(unlinked, plan(nutritionist, unlinked, null), today.minusDays(1), "DONE");
            return delta(before);
        });

        assertThat(after.get(NutritionMetrics.LINKED)).isEqualTo(3);
        assertThat(after.get(NutritionMetrics.ENGAGED)).isEqualTo(1);
    }

    @Test
    void prescriptionsCountPlanVersionsPublishedInTheLast30Days() {
        Map<String, Double> before = values();
        Instant now = Instant.now();
        UUID nutritionist = UUID.randomUUID();
        UUID plan = plan(
                nutritionist, UUID.randomUUID(), now.minus(Duration.ofDays(30)).plus(Duration.ofMinutes(1)));
        version(plan, 2, now.minus(Duration.ofDays(1)));
        version(plan, 3, null);
        plan(nutritionist, UUID.randomUUID(), now.minus(Duration.ofDays(30)).minus(Duration.ofMinutes(1)));
        plan(UUID.randomUUID(), UUID.randomUUID(), now.minus(Duration.ofDays(2)));
        // Nutricionista que só publicou antes dos 30 dias: não está entre as que publicaram.
        plan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.minus(Duration.ofDays(30)).minus(Duration.ofMinutes(1)));

        Map<String, Double> after = delta(before);

        assertThat(after.get(NutritionMetrics.PUBLISHED)).isEqualTo(3);
        assertThat(after.get(NutritionMetrics.PRESCRIBERS)).isEqualTo(2);
    }

    @Test
    void useAfterTheLinkEndsCountsClientsWithoutAnActiveLinkWhoAteAfterTheEndDay() {
        Map<String, Double> after = onOneDay(today -> {
            Map<String, Double> before = values();
            Instant now = Instant.now();
            UUID nutritionist = UUID.randomUUID();
            UUID kept = ended(nutritionist, now.minus(Duration.ofDays(10)));
            UUID keptPlan = plan(nutritionist, kept, null);
            meal(kept, keptPlan, today.minusDays(2), "DONE");
            // Comeu só antes do fim do vínculo; e pulou depois.
            UUID stopped = ended(nutritionist, now.minus(Duration.ofDays(5)));
            UUID stoppedPlan = plan(nutritionist, stopped, null);
            meal(stopped, stoppedPlan, today.minusDays(6), "DONE");
            meal(stopped, stoppedPlan, today.minusDays(1), "SKIPPED");
            // Encerrou um vínculo e tem outro ativo: ainda é cliente vinculado.
            UUID relinked = ended(nutritionist, now.minus(Duration.ofDays(20)));
            link(relinked, UUID.randomUUID(), "ACTIVE", null);
            meal(relinked, plan(nutritionist, relinked, null), today, "DONE");
            // Comeu depois do fim do vínculo, mas há mais de 30 dias (o 30º dia antes de hoje já fica fora).
            UUID old = ended(nutritionist, now.minus(Duration.ofDays(60)));
            meal(old, plan(nutritionist, old, null), today.minusDays(30), "DONE");
            return delta(before);
        });

        assertThat(after.get(NutritionMetrics.AFTER_LINK_END)).isEqualTo(1);
    }

    /**
     * Roda o cenário com o dia de hoje em São Paulo e repete uma vez se a meia-noite cair no meio dele: os limites da
     * nutrição são em dias, e a métrica calcula com o dia do momento em que consulta (Code Review do PR #326).
     */
    private Map<String, Double> onOneDay(Function<LocalDate, Map<String, Double>> scenario) {
        for (int attempt = 0; attempt < 2; attempt++) {
            LocalDate today = LocalDate.now(NutritionMetrics.ZONE);
            Map<String, Double> result = scenario.apply(today);
            if (today.equals(LocalDate.now(NutritionMetrics.ZONE))) {
                return result;
            }
        }
        throw new IllegalStateException("O dia de São Paulo mudou duas vezes durante o teste.");
    }

    @Test
    void theMetricsOnlyCarryTheTypeTag() {
        for (String name : names()) {
            Meter meter = registry.get(name).meter();
            assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getKey() + "=" + tag.getValue())
                    .containsExactly("type=NUTRITIONIST");
        }
    }

    private static String[] names() {
        return new String[] {
            NutritionMetrics.LINKED,
            NutritionMetrics.ENGAGED,
            NutritionMetrics.PUBLISHED,
            NutritionMetrics.PRESCRIBERS,
            NutritionMetrics.AFTER_LINK_END
        };
    }

    private Map<String, Double> values() {
        gauges.refresh();
        Map<String, Double> values = new HashMap<>();
        for (String name : names()) {
            values.put(name, registry.get(name).gauge().value());
        }
        return values;
    }

    private Map<String, Double> delta(Map<String, Double> before) {
        Map<String, Double> after = values();
        after.replaceAll((name, value) -> value - before.get(name));
        return after;
    }

    private UUID linked(UUID nutritionist) {
        UUID client = UUID.randomUUID();
        link(client, nutritionist, "ACTIVE", null);
        return client;
    }

    private UUID ended(UUID nutritionist, Instant endedAt) {
        UUID client = UUID.randomUUID();
        link(client, nutritionist, "ENDED", endedAt);
        return client;
    }

    private void link(UUID client, UUID nutritionist, String status, Instant endedAt) {
        jdbc.sql("insert into client_link (link_id, client_id, professional_id, status, activated_at, ended_at)"
                        + " values (?, ?, ?, ?, now() - interval '90 days', ?)")
                .params(
                        UUID.randomUUID(),
                        client,
                        nutritionist,
                        status,
                        endedAt == null ? null : Timestamp.from(endedAt))
                .update();
    }

    /** Plano com a versão 1 publicada em {@code publishedAt} (nula: há 40 dias, fora da janela de prescrições). */
    private UUID plan(UUID nutritionist, UUID client, Instant publishedAt) {
        UUID plan = UUID.randomUUID();
        jdbc.sql("insert into meal_plan (id, client_id, nutritionist_id, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'REPLACED', now(), now())")
                .params(plan, client, nutritionist)
                .update();
        version(plan, 1, publishedAt == null ? Instant.now().minus(Duration.ofDays(40)) : publishedAt);
        return plan;
    }

    private void version(UUID plan, int number, Instant publishedAt) {
        jdbc.sql("insert into meal_plan_version (id, plan_id, number, name, starts_on, published_at, created_at)"
                        + " values (?, ?, ?, 'Plano de teste', current_date, ?, now())")
                .params(UUID.randomUUID(), plan, number, publishedAt == null ? null : Timestamp.from(publishedAt))
                .update();
    }

    private void meal(UUID client, UUID plan, LocalDate day, String status) {
        UUID version = jdbc.sql("select id from meal_plan_version where plan_id = ? and number = 1")
                .param(plan)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into meal_log (id, client_id, plan_id, version_id, version_number, meal_position, logged_on,"
                        + " status, created_at, updated_at) values (?, ?, ?, ?, 1, 0, ?, ?, now(), now())")
                .params(UUID.randomUUID(), client, plan, version, day, status)
                .update();
    }
}
