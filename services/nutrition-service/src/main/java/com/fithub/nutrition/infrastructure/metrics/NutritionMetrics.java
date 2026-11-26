package com.fithub.nutrition.infrastructure.metrics;

import com.fithub.commons.metrics.AggregateGauges;
import io.micrometer.core.instrument.Tags;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.LongSupplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Métricas de sucesso do PRD da nutrição (DEV-0113), só contagens com {@code type=NUTRITIONIST}; as mesmas do
 * training-service, com {@code type=PERSONAL}.
 * <ul>
 * <li><b>Engajamento:</b> clientes com vínculo ativo com nutricionista e, entre eles, os que registraram uma refeição
 * feita ou parcial (a pulada não conta) num dos últimos 7 dias, no fuso de São Paulo, como o registro.</li>
 * <li><b>Prescrições:</b> versões de plano alimentar publicadas nos últimos 30 dias, e quantas nutricionistas
 * publicaram.</li>
 * <li><b>Uso depois do vínculo:</b> clientes sem vínculo ativo, com vínculo encerrado, que registraram refeição feita
 * ou parcial num dia depois do fim do último vínculo, nos últimos 30 dias.</li>
 * </ul>
 */
@Component
class NutritionMetrics {

    static final String LINKED = "fithub.clients.linked";
    static final String ENGAGED = "fithub.clients.linked.engaged";
    static final String PUBLISHED = "fithub.prescriptions.published";
    static final String PRESCRIBERS = "fithub.prescribers.active";
    static final String AFTER_LINK_END = "fithub.clients.after.link.end";

    /** O dia do registro de refeição é o de São Paulo. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String EATEN = "m.status in ('DONE', 'PARTIAL')";
    private static final Duration MONTH = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final Clock clock;

    NutritionMetrics(AggregateGauges gauges, JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        Tags nutritionist = Tags.of("type", "NUTRITIONIST");
        gauges.register(
                LINKED,
                "Clientes com vínculo ativo",
                nutritionist,
                () -> jdbc.sql("select count(distinct client_id) from client_link where status = 'ACTIVE'")
                        .query(Long.class)
                        .single());
        gauges.register(
                ENGAGED,
                "Clientes com vínculo ativo que registraram atividade nos últimos 7 dias",
                nutritionist,
                byDay(
                        "select count(distinct l.client_id) from client_link l where l.status = 'ACTIVE' and exists ("
                                + "select 1 from meal_log m where m.client_id = l.client_id and " + EATEN
                                + " and m.logged_on >= :since)",
                        7));
        gauges.register(
                PUBLISHED,
                "Prescrições publicadas pelo profissional nos últimos 30 dias",
                nutritionist,
                byInstant("select count(*) from meal_plan_version v where v.published_at >= :since", MONTH));
        gauges.register(
                PRESCRIBERS,
                "Profissionais que publicaram prescrição nos últimos 30 dias",
                nutritionist,
                byInstant(
                        "select count(distinct p.nutritionist_id) from meal_plan_version v"
                                + " join meal_plan p on p.id = v.plan_id where v.published_at >= :since",
                        MONTH));
        gauges.register(
                AFTER_LINK_END,
                "Clientes sem vínculo ativo que registraram atividade depois do fim do vínculo, nos últimos 30 dias",
                nutritionist,
                byDay(
                        "select count(*) from (select client_id, max(ended_at) as last_end from client_link"
                                + " group by client_id having bool_and(status = 'ENDED')) ended where exists ("
                                + "select 1 from meal_log m where m.client_id = ended.client_id and " + EATEN
                                + " and m.logged_on > (ended.last_end at time zone 'America/Sao_Paulo')::date"
                                + " and m.logged_on >= :since)",
                        30));
    }

    /** Janela em dias de São Paulo: hoje e os {@code days - 1} anteriores. */
    private LongSupplier byDay(String sql, int days) {
        return () -> jdbc.sql(sql)
                .param("since", LocalDate.ofInstant(clock.instant(), ZONE).minusDays(days - 1L))
                .query(Long.class)
                .single();
    }

    private LongSupplier byInstant(String sql, Duration window) {
        return () -> jdbc.sql(sql)
                .param("since", Timestamp.from(clock.instant().minus(window)))
                .query(Long.class)
                .single();
    }
}
