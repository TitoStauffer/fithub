package com.fithub.account.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.metrics.AggregateGauges;
import com.fithub.testing.service.ServiceIntegrationTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/** Profissionais por tipo e estado da assinatura (DEV-0113), só a contagem. */
@TestPropertySource(properties = "fithub.professional.role-retry.enabled=false")
class SubscriptionMetricsTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AggregateGauges gauges;

    @Autowired
    MeterRegistry registry;

    @Test
    void countsProfessionalsByTypeAndSubscriptionState() {
        // Confere o acréscimo sobre o banco, que é compartilhado com as outras classes de teste.
        gauges.refresh();
        Map<String, Double> before = values();
        Instant now = Instant.now();
        // Em teste, com 1 minuto de teste ainda.
        professional("PERSONAL", now.plus(Duration.ofMinutes(1)), null);
        // Teste vencido há 1 minuto, sem pagamento: expirado.
        professional("PERSONAL", now.minus(Duration.ofMinutes(1)), null);
        // Pago até amanhã, teste já vencido: ativo (pagante).
        professional("NUTRITIONIST", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));
        // Pagamento vencido, teste vencido: expirado.
        professional("NUTRITIONIST", now.minus(Duration.ofDays(30)), now.minus(Duration.ofMinutes(1)));
        // Pago e ainda em teste: conta como ativo, não em teste.
        professional("NUTRITIONIST", now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(30)));

        gauges.refresh();
        Map<String, Double> after = values();
        after.replaceAll((key, value) -> value - before.get(key));

        assertThat(after)
                .containsEntry("PERSONAL TRIAL", 1.0)
                .containsEntry("PERSONAL EXPIRED", 1.0)
                .containsEntry("PERSONAL ACTIVE", 0.0)
                .containsEntry("NUTRITIONIST ACTIVE", 2.0)
                .containsEntry("NUTRITIONIST EXPIRED", 1.0)
                .containsEntry("NUTRITIONIST TRIAL", 0.0);
    }

    private Map<String, Double> values() {
        Map<String, Double> values = new HashMap<>();
        for (String type : new String[] {"PERSONAL", "NUTRITIONIST"}) {
            for (String status : new String[] {"TRIAL", "ACTIVE", "EXPIRED"}) {
                values.put(type + " " + status, value(type, status));
            }
        }
        return values;
    }

    @Test
    void theMetricOnlyCarriesTypeAndStatusTags() {
        for (Meter meter : registry.find(SubscriptionMetrics.PROFESSIONALS).meters()) {
            assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getKey())
                    .containsExactlyInAnyOrder("type", "status");
        }
        assertThat(registry.find(SubscriptionMetrics.PROFESSIONALS).meters()).hasSize(6);
    }

    private double value(String type, String status) {
        return registry.get(SubscriptionMetrics.PROFESSIONALS)
                .tag("type", type)
                .tag("status", status)
                .gauge()
                .value();
    }

    private void professional(String type, Instant trialEndsAt, Instant paidUntil) {
        UUID organization = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into organization (id, owner_account_id, created_at) values (?, ?, now())")
                .params(organization, id.toString())
                .update();
        boolean personal = type.equals("PERSONAL");
        jdbc.sql("insert into professional (id, account_id, type, council, registration_number, registration_category,"
                        + " registration_state, situation, organization_id, role_status, created_at, name)"
                        + " values (?, ?, ?, ?, '123', ?, 'SP', 'DECLARED', ?, 'ASSIGNED', now() - interval '60 days',"
                        + " 'Profissional de teste')")
                .params(id, id.toString(), type, personal ? "CREF" : "CRN", personal ? "G" : null, organization)
                .update();
        jdbc.sql("insert into subscription (professional_id, plan, trial_ends_at, paid_until, created_at)"
                        + " values (?, 'PROFESSIONAL_MONTHLY', ?, ?, now() - interval '60 days')")
                .params(id, Timestamp.from(trialEndsAt), paidUntil == null ? null : Timestamp.from(paidUntil))
                .update();
    }
}
