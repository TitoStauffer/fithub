package com.fithub.training.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.metrics.AggregateGauges;
import com.fithub.testing.service.ServiceIntegrationTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Métricas de sucesso do treino (DEV-0113). Cada caso confere o acréscimo sobre o banco, com datas relativas ao
 * relógio e 1 minuto de folga em cada limite.
 */
class TrainingMetricsTest extends ServiceIntegrationTest {

    private static final Duration MARGIN = Duration.ofMinutes(1);

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AggregateGauges gauges;

    @Autowired
    MeterRegistry registry;

    @Test
    void engagementCountsLinkedClientsWithAWorkoutInTheLast7Days() {
        Map<String, Double> before = values();
        Instant now = Instant.now();
        UUID personal = UUID.randomUUID();
        UUID engaged = linked(personal);
        session(
                engaged,
                plan(personal, engaged, null),
                now.minus(TrainingMetrics.WEEK).plus(MARGIN));
        UUID stale = linked(personal);
        session(
                stale,
                plan(personal, stale, null),
                now.minus(TrainingMetrics.WEEK).minus(MARGIN));
        linked(personal);
        // Cliente com vínculo encerrado que treinou nesta semana não entra no engajamento dos vinculados.
        UUID unlinked = ended(personal, now.minus(Duration.ofDays(40)));
        session(unlinked, ownPlan(unlinked), now.minus(Duration.ofDays(1)));

        Map<String, Double> after = delta(before);

        assertThat(after.get(TrainingMetrics.LINKED)).isEqualTo(3);
        assertThat(after.get(TrainingMetrics.ENGAGED)).isEqualTo(1);
    }

    @Test
    void prescriptionsCountVersionsPublishedByThePersonalInTheLast30Days() {
        Map<String, Double> before = values();
        Instant now = Instant.now();
        UUID personal = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID plan = plan(personal, client, now.minus(TrainingMetrics.MONTH).plus(MARGIN));
        version(plan, 2, now.minus(Duration.ofDays(1)));
        // Publicada antes da janela, rascunho e ficha do próprio cliente não contam.
        plan(personal, UUID.randomUUID(), now.minus(TrainingMetrics.MONTH).minus(MARGIN));
        version(plan, 3, null);
        UUID own = ownPlan(client);
        version(own, 2, now.minus(Duration.ofDays(1)));
        plan(UUID.randomUUID(), UUID.randomUUID(), now.minus(Duration.ofDays(2)));

        Map<String, Double> after = delta(before);

        assertThat(after.get(TrainingMetrics.PUBLISHED)).isEqualTo(3);
        assertThat(after.get(TrainingMetrics.PRESCRIBERS)).isEqualTo(2);
    }

    @Test
    void useAfterTheLinkEndsCountsClientsWithoutAnActiveLinkWhoTrainedAfterTheEnd() {
        Map<String, Double> before = values();
        Instant now = Instant.now();
        UUID personal = UUID.randomUUID();
        UUID kept = ended(personal, now.minus(Duration.ofDays(10)));
        session(kept, ownPlan(kept), now.minus(Duration.ofDays(2)));
        // Treinou só antes do fim do vínculo.
        UUID stopped = ended(personal, now.minus(Duration.ofDays(5)));
        session(stopped, plan(personal, stopped, null), now.minus(Duration.ofDays(6)));
        // Treinou depois do fim, mas há mais de 30 dias.
        UUID old = ended(personal, now.minus(Duration.ofDays(60)));
        session(old, ownPlan(old), now.minus(TrainingMetrics.MONTH).minus(MARGIN));
        // Encerrou um vínculo e tem outro ativo: ainda é cliente vinculado.
        UUID relinked = ended(personal, now.minus(Duration.ofDays(20)));
        link(relinked, UUID.randomUUID(), "ACTIVE", null);
        session(relinked, ownPlan(relinked), now.minus(Duration.ofDays(1)));

        Map<String, Double> after = delta(before);

        assertThat(after.get(TrainingMetrics.AFTER_LINK_END)).isEqualTo(1);
    }

    @Test
    void theMetricsOnlyCarryTheTypeTag() {
        for (String name : names()) {
            Meter meter = registry.get(name).meter();
            assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getKey() + "=" + tag.getValue())
                    .containsExactly("type=PERSONAL");
        }
    }

    private static String[] names() {
        return new String[] {
            TrainingMetrics.LINKED,
            TrainingMetrics.ENGAGED,
            TrainingMetrics.PUBLISHED,
            TrainingMetrics.PRESCRIBERS,
            TrainingMetrics.AFTER_LINK_END
        };
    }

    private Map<String, Double> values() {
        gauges.refresh();
        Map<String, Double> values = new java.util.HashMap<>();
        for (String name : names()) values.put(name, registry.get(name).gauge().value());
        return values;
    }

    private Map<String, Double> delta(Map<String, Double> before) {
        Map<String, Double> after = values();
        after.replaceAll((name, value) -> value - before.get(name));
        return after;
    }

    private UUID linked(UUID personal) {
        UUID client = UUID.randomUUID();
        link(client, personal, "ACTIVE", null);
        return client;
    }

    private UUID ended(UUID personal, Instant endedAt) {
        UUID client = UUID.randomUUID();
        link(client, personal, "ENDED", endedAt);
        return client;
    }

    private void link(UUID client, UUID personal, String status, Instant endedAt) {
        jdbc.sql("insert into client_link (link_id, client_id, professional_id, status, activated_at, ended_at)"
                        + " values (?, ?, ?, ?, now() - interval '90 days', ?)")
                .params(UUID.randomUUID(), client, personal, status, endedAt == null ? null : Timestamp.from(endedAt))
                .update();
    }

    /** Ficha do personal com a versão 1 publicada em {@code publishedAt} (nula: rascunho). */
    private UUID plan(UUID personal, UUID client, Instant publishedAt) {
        return insertPlan(personal, client, "PERSONAL", publishedAt);
    }

    private UUID ownPlan(UUID client) {
        return insertPlan(null, client, "CLIENT", Instant.now().minus(Duration.ofDays(1)));
    }

    private UUID insertPlan(UUID personal, UUID client, String author, Instant publishedAt) {
        UUID plan = UUID.randomUUID();
        jdbc.sql("insert into workout_plan (id, client_id, personal_id, status, author, created_at, updated_at)"
                        + " values (?, ?, ?, 'REPLACED', ?, now(), now())")
                .params(plan, client, personal, author)
                .update();
        version(plan, 1, publishedAt);
        return plan;
    }

    private void version(UUID plan, int number, Instant publishedAt) {
        jdbc.sql("insert into workout_plan_version (id, plan_id, number, name, starts_on, validity_weeks, published_at,"
                        + " created_at) values (?, ?, ?, 'Ficha de teste', current_date, 4, ?, now())")
                .params(UUID.randomUUID(), plan, number, publishedAt == null ? null : Timestamp.from(publishedAt))
                .update();
    }

    private void session(UUID client, UUID plan, Instant startedAt) {
        UUID version = jdbc.sql("select id from workout_plan_version where plan_id = ? and number = 1")
                .param(plan)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into workout_session (id, client_id, plan_id, version_id, division_position, started_at,"
                        + " finished_at) values (?, ?, ?, ?, 0, ?, ?)")
                .params(
                        UUID.randomUUID(),
                        client,
                        plan,
                        version,
                        Timestamp.from(startedAt),
                        Timestamp.from(startedAt.plus(Duration.ofHours(1))))
                .update();
    }
}
