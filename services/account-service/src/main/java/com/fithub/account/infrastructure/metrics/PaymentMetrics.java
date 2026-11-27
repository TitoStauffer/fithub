package com.fithub.account.infrastructure.metrics;

import com.fithub.commons.metrics.AggregateGauges;
import io.micrometer.core.instrument.Tags;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Métricas de sucesso do PRD que dependem do pagamento (DEV-0100, DEV-0113, PRD seção 19), só contagens por tipo de
 * profissional, a partir do histórico de cobranças confirmadas.
 * <ul>
 * <li><b>Conversão do teste para pago:</b> profissionais com o teste grátis já terminado e, entre eles, os que têm ao
 * menos uma cobrança confirmada. O painel divide um pelo outro.</li>
 * <li><b>Retenção mensal:</b> profissionais com cobrança confirmada que vence no mês passado e, entre eles, os que
 * também têm cobrança confirmada que vence neste mês (meses de São Paulo).</li>
 * </ul>
 */
@Component
class PaymentMetrics {

    static final String TRIAL_ENDED = "fithub.professionals.trial.ended";
    static final String CONVERTED = "fithub.professionals.converted";
    static final String PAID_LAST_MONTH = "fithub.professionals.paid.last.month";
    static final String RETAINED = "fithub.professionals.retained";

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    PaymentMetrics(AggregateGauges gauges, JdbcClient jdbc, Clock clock) {
        for (String type : new String[] {"PERSONAL", "NUTRITIONIST"}) {
            Tags tags = Tags.of("type", type);
            gauges.register(
                    TRIAL_ENDED,
                    "Profissionais com o teste grátis já terminado",
                    tags,
                    () -> jdbc.sql("select count(*) from subscription s join professional p on p.id = s.professional_id"
                                    + " where p.type = :type and s.trial_ends_at <= :now")
                            .param("type", type)
                            .param("now", Timestamp.from(clock.instant()))
                            .query(Long.class)
                            .single());
            gauges.register(
                    CONVERTED,
                    "Profissionais com o teste terminado e ao menos uma cobrança confirmada",
                    tags,
                    () -> jdbc.sql("select count(*) from subscription s join professional p on p.id = s.professional_id"
                                    + " where p.type = :type and s.trial_ends_at <= :now and exists ("
                                    + "select 1 from subscription_payment m where m.professional_id = p.id)")
                            .param("type", type)
                            .param("now", Timestamp.from(clock.instant()))
                            .query(Long.class)
                            .single());
            gauges.register(
                    PAID_LAST_MONTH, "Profissionais com cobrança confirmada que vence no mês passado", tags, () -> {
                        YearMonth last = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZONE))
                                .minusMonths(1);
                        return paidIn(jdbc, type, last);
                    });
            gauges.register(
                    RETAINED,
                    "Profissionais pagos no mês passado que também têm cobrança confirmada neste mês",
                    tags,
                    () -> {
                        YearMonth current = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZONE));
                        YearMonth last = current.minusMonths(1);
                        return jdbc.sql("select count(distinct p.id) from professional p where p.type = :type"
                                        + " and exists (select 1 from subscription_payment m where m.professional_id = p.id"
                                        + " and m.due_date between :lastStart and :lastEnd)"
                                        + " and exists (select 1 from subscription_payment m where m.professional_id = p.id"
                                        + " and m.due_date between :currentStart and :currentEnd)")
                                .param("type", type)
                                .param("lastStart", last.atDay(1))
                                .param("lastEnd", last.atEndOfMonth())
                                .param("currentStart", current.atDay(1))
                                .param("currentEnd", current.atEndOfMonth())
                                .query(Long.class)
                                .single();
                    });
        }
    }

    private static long paidIn(JdbcClient jdbc, String type, YearMonth month) {
        return jdbc.sql("select count(distinct p.id) from professional p where p.type = :type and exists ("
                        + "select 1 from subscription_payment m where m.professional_id = p.id"
                        + " and m.due_date between :start and :end)")
                .param("type", type)
                .param("start", month.atDay(1))
                .param("end", month.atEndOfMonth())
                .query(Long.class)
                .single();
    }
}
