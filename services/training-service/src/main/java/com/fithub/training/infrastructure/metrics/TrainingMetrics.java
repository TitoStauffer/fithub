package com.fithub.training.infrastructure.metrics;

import com.fithub.commons.metrics.AggregateGauges;
import io.micrometer.core.instrument.Tags;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Métricas de sucesso do PRD do treino (DEV-0113), só contagens com {@code type=PERSONAL}. O nutrition-service publica
 * as mesmas, com {@code type=NUTRITIONIST}; o painel junta as duas.
 * <ul>
 * <li><b>Engajamento:</b> clientes com vínculo ativo com personal e, entre eles, os que iniciaram um treino nos últimos
 * 7 dias.</li>
 * <li><b>Prescrições:</b> versões de ficha publicadas pelo personal nos últimos 30 dias, e quantos personais
 * publicaram. A ficha que o próprio cliente monta não conta.</li>
 * <li><b>Uso depois do vínculo:</b> clientes sem vínculo ativo, com vínculo encerrado, que iniciaram um treino depois do
 * fim do último vínculo, nos últimos 30 dias.</li>
 * </ul>
 */
@Component
class TrainingMetrics {

    static final String LINKED = "fithub.clients.linked";
    static final String ENGAGED = "fithub.clients.linked.engaged";
    static final String PUBLISHED = "fithub.prescriptions.published";
    static final String PRESCRIBERS = "fithub.prescribers.active";
    static final String AFTER_LINK_END = "fithub.clients.after.link.end";

    static final Duration WEEK = Duration.ofDays(7);
    static final Duration MONTH = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final Clock clock;

    TrainingMetrics(AggregateGauges gauges, JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        Tags personal = Tags.of("type", "PERSONAL");
        gauges.register(
                LINKED,
                "Clientes com vínculo ativo",
                personal,
                count("select count(distinct client_id) from client_link where status = 'ACTIVE'", null));
        gauges.register(
                ENGAGED,
                "Clientes com vínculo ativo que registraram atividade nos últimos 7 dias",
                personal,
                count(
                        "select count(distinct l.client_id) from client_link l where l.status = 'ACTIVE' and exists ("
                                + "select 1 from workout_session s where s.client_id = l.client_id"
                                + " and s.started_at >= :since)",
                        WEEK));
        gauges.register(
                PUBLISHED,
                "Prescrições publicadas pelo profissional nos últimos 30 dias",
                personal,
                count(
                        "select count(*) from workout_plan_version v join workout_plan p on p.id = v.plan_id"
                                + " where p.author = 'PERSONAL' and v.published_at >= :since",
                        MONTH));
        gauges.register(
                PRESCRIBERS,
                "Profissionais que publicaram prescrição nos últimos 30 dias",
                personal,
                count(
                        "select count(distinct p.personal_id) from workout_plan_version v"
                                + " join workout_plan p on p.id = v.plan_id"
                                + " where p.author = 'PERSONAL' and v.published_at >= :since",
                        MONTH));
        gauges.register(
                AFTER_LINK_END,
                "Clientes sem vínculo ativo que registraram atividade depois do fim do vínculo, nos últimos 30 dias",
                personal,
                count(
                        "select count(*) from (select client_id, max(ended_at) as last_end from client_link"
                                + " group by client_id having bool_and(status = 'ENDED')) ended where exists ("
                                + "select 1 from workout_session s where s.client_id = ended.client_id"
                                + " and s.started_at > ended.last_end and s.started_at >= :since)",
                        MONTH));
    }

    /** Contagem; com {@code window}, o parâmetro {@code :since} é o início da janela até agora. */
    private LongSupplier count(String sql, Duration window) {
        return () -> {
            var statement = jdbc.sql(sql);
            if (window != null) {
                statement =
                        statement.param("since", Timestamp.from(clock.instant().minus(window)));
            }
            return statement.query(Long.class).single();
        };
    }
}
