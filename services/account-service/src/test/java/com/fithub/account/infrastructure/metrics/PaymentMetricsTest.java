package com.fithub.account.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.metrics.AggregateGauges;
import com.fithub.testing.service.ServiceIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * Conversão do teste para pago e retenção mensal (DEV-0100, DEV-0113), só contagens por tipo. Confere o acréscimo sobre
 * o banco, que é compartilhado com as outras classes de teste, com os limites de cada mês.
 */
@TestPropertySource(properties = "fithub.professional.role-retry.enabled=false")
class PaymentMetricsTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AggregateGauges gauges;

    @Autowired
    MeterRegistry registry;

    @Test
    void countsConversionAndRetentionByTypeWithTheMonthLimits() {
        Map<String, Double> after = onOneMonth(current -> {
            YearMonth last = current.minusMonths(1);
            Map<String, Double> before = values();
            Instant now = Instant.now();
            Instant ended = now.minus(Duration.ofDays(40));
            // Pagou no 1º dia do mês passado e no último dia deste: convertida, paga no mês passado e retida.
            professional("NUTRITIONIST", ended, last.atDay(1), current.atEndOfMonth());
            // Teste terminado, sem pagamento: entra só no "teste terminado".
            professional("NUTRITIONIST", ended);
            // Ainda em teste, já com pagamento: não conta na conversão.
            professional("NUTRITIONIST", now.plus(Duration.ofDays(3)), last.atDay(15));
            // Pagou só no mês passado: paga no mês passado, não retida.
            professional("NUTRITIONIST", ended, last.atEndOfMonth());
            // Pagou só no dia antes do mês passado: fora do mês passado.
            professional("NUTRITIONIST", ended, last.atDay(1).minusDays(1));
            // Pagou no mês passado e só no dia seguinte ao fim deste mês: não retida.
            professional(
                    "NUTRITIONIST",
                    ended,
                    last.atDay(10),
                    current.atEndOfMonth().plusDays(1));
            // O mesmo caso da primeira, mas personal: conta só no tipo dele.
            // Pagou no dia antes do mês passado e neste mês: não estava pago no mês passado, então não é retida.
            professional("NUTRITIONIST", ended, last.atDay(1).minusDays(1), current.atDay(2));
            // Pagou só neste mês: convertida, mas fora do mês passado e da retenção.
            professional("NUTRITIONIST", ended, current.atDay(1));
            professional("PERSONAL", ended, last.atDay(1), current.atDay(1));
            return delta(before);
        });

        assertThat(after)
                .containsEntry("NUTRITIONIST " + PaymentMetrics.TRIAL_ENDED, 7.0)
                .containsEntry("NUTRITIONIST " + PaymentMetrics.CONVERTED, 6.0)
                .containsEntry("NUTRITIONIST " + PaymentMetrics.PAID_LAST_MONTH, 4.0)
                .containsEntry("NUTRITIONIST " + PaymentMetrics.RETAINED, 1.0)
                .containsEntry("PERSONAL " + PaymentMetrics.TRIAL_ENDED, 1.0)
                .containsEntry("PERSONAL " + PaymentMetrics.CONVERTED, 1.0)
                .containsEntry("PERSONAL " + PaymentMetrics.PAID_LAST_MONTH, 1.0)
                .containsEntry("PERSONAL " + PaymentMetrics.RETAINED, 1.0);
    }

    @Test
    void theMetricsOnlyCarryTheTypeTag() {
        for (String name : names()) {
            assertThat(registry.find(name).meters())
                    .hasSize(2)
                    .allSatisfy(meter -> assertThat(meter.getId().getTags())
                            .extracting(tag -> tag.getKey())
                            .containsExactly("type"));
        }
    }

    /** Repete o cenário uma vez se o mês de São Paulo virar no meio dele: os limites são do mês. */
    private Map<String, Double> onOneMonth(Function<YearMonth, Map<String, Double>> scenario) {
        for (int attempt = 0; attempt < 2; attempt++) {
            YearMonth current = YearMonth.now(PaymentMetrics.ZONE);
            Map<String, Double> result = scenario.apply(current);
            if (current.equals(YearMonth.now(PaymentMetrics.ZONE))) {
                return result;
            }
        }
        throw new IllegalStateException("O mês de São Paulo mudou duas vezes durante o teste.");
    }

    private static String[] names() {
        return new String[] {
            PaymentMetrics.TRIAL_ENDED,
            PaymentMetrics.CONVERTED,
            PaymentMetrics.PAID_LAST_MONTH,
            PaymentMetrics.RETAINED
        };
    }

    private Map<String, Double> values() {
        gauges.refresh();
        Map<String, Double> values = new HashMap<>();
        for (String type : new String[] {"PERSONAL", "NUTRITIONIST"}) {
            for (String name : names()) {
                values.put(
                        type + " " + name,
                        registry.get(name).tag("type", type).gauge().value());
            }
        }
        return values;
    }

    private Map<String, Double> delta(Map<String, Double> before) {
        Map<String, Double> after = values();
        after.replaceAll((key, value) -> value - before.get(key));
        return after;
    }

    /** Profissional com o teste terminando em {@code trialEndsAt} e uma cobrança confirmada em cada vencimento. */
    private void professional(String type, Instant trialEndsAt, LocalDate... dueDates) {
        UUID organization = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into organization (id, owner_account_id, created_at) values (?, ?, now())")
                .params(organization, id.toString())
                .update();
        boolean personal = type.equals("PERSONAL");
        jdbc.sql("insert into professional (id, account_id, type, council, registration_number, registration_category,"
                        + " registration_state, situation, organization_id, role_status, created_at, name)"
                        + " values (?, ?, ?, ?, '123', ?, 'SP', 'DECLARED', ?, 'ASSIGNED', now() - interval '90 days',"
                        + " 'Profissional de teste')")
                .params(id, id.toString(), type, personal ? "CREF" : "CRN", personal ? "G" : null, organization)
                .update();
        jdbc.sql("insert into subscription (professional_id, plan, trial_ends_at, paid_until, created_at)"
                        + " values (?, 'PROFESSIONAL_MONTHLY', ?, null, now() - interval '90 days')")
                .params(id, Timestamp.from(trialEndsAt))
                .update();
        if (dueDates.length == 0) {
            return;
        }
        String checkout = "chk_" + UUID.randomUUID();
        String subscription = "sub_" + UUID.randomUUID();
        jdbc.sql("insert into payment_checkout (id, link, professional_id, launch, value, created_at)"
                        + " values (?, 'https://sandbox.asaas.com/teste', ?, true, 150, now())")
                .params(checkout, id)
                .update();
        jdbc.sql(
                        "insert into payment_subscription (id, checkout_id, professional_id, created_at) values (?, ?, ?, now())")
                .params(subscription, checkout, id)
                .update();
        for (LocalDate due : dueDates) {
            jdbc.sql("insert into subscription_payment (payment_id, subscription_id, professional_id, value, due_date,"
                            + " confirmed_at) values (?, ?, ?, 150, ?, now())")
                    .params("pay_" + UUID.randomUUID(), subscription, id, due)
                    .update();
        }
    }
}
