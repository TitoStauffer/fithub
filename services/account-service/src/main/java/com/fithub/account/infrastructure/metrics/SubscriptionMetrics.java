package com.fithub.account.infrastructure.metrics;

import com.fithub.commons.metrics.AggregateGauges;
import io.micrometer.core.instrument.Tags;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Métricas de sucesso do PRD da assinatura (DEV-0113): profissionais por tipo e por estado da assinatura, só a
 * contagem. O estado segue {@code Subscription.status()}: ativo enquanto o período pago vale, em teste enquanto o
 * teste grátis vale, expirado depois. "Pagantes" são os ativos; ficam em zero até o pagamento existir (DEV-0100).
 */
@Component
class SubscriptionMetrics {

    static final String PROFESSIONALS = "fithub.professionals";

    private static final String ACTIVE = "s.paid_until > :now";
    private static final String TRIAL = "not coalesce(s.paid_until > :now, false) and s.trial_ends_at > :now";
    private static final String EXPIRED = "not coalesce(s.paid_until > :now, false) and s.trial_ends_at <= :now";

    SubscriptionMetrics(AggregateGauges gauges, JdbcClient jdbc, Clock clock) {
        for (String type : new String[] {"PERSONAL", "NUTRITIONIST"}) {
            register(gauges, jdbc, clock, type, "ACTIVE", ACTIVE);
            register(gauges, jdbc, clock, type, "TRIAL", TRIAL);
            register(gauges, jdbc, clock, type, "EXPIRED", EXPIRED);
        }
    }

    private static void register(
            AggregateGauges gauges, JdbcClient jdbc, Clock clock, String type, String status, String condition) {
        gauges.register(
                PROFESSIONALS,
                "Profissionais por tipo e estado da assinatura",
                Tags.of("type", type, "status", status),
                () -> jdbc.sql("select count(*) from subscription s join professional p on p.id = s.professional_id"
                                + " where p.type = :type and " + condition)
                        .param("type", type)
                        .param("now", Timestamp.from(clock.instant()))
                        .query(Long.class)
                        .single());
    }
}
