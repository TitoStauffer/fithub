package com.fithub.client.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.commons.metrics.AggregateGauges;
import com.fithub.testing.service.ServiceIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Vínculos ativos e profissionais com vínculo ativo, por tipo (DEV-0113). Confere o acréscimo sobre o banco. */
class LinkMetricsTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AggregateGauges gauges;

    @Autowired
    MeterRegistry registry;

    @Test
    void countsActiveLinksAndProfessionalsWithAnActiveLinkByType() {
        gauges.refresh();
        double links = value(LinkMetrics.ACTIVE_LINKS, "PERSONAL");
        double personals = value(LinkMetrics.PROFESSIONALS_WITH_ACTIVE_LINKS, "PERSONAL");
        double nutritionLinks = value(LinkMetrics.ACTIVE_LINKS, "NUTRITIONIST");
        double nutritionists = value(LinkMetrics.PROFESSIONALS_WITH_ACTIVE_LINKS, "NUTRITIONIST");

        UUID personal = professional("PERSONAL_TRAINER");
        link(personal, "PERSONAL_TRAINER", "ACTIVE");
        link(personal, "PERSONAL_TRAINER", "ACTIVE");
        // Vínculo encerrado não conta, nem o profissional que só tem vínculo encerrado.
        link(personal, "PERSONAL_TRAINER", "ENDED");
        link(professional("PERSONAL_TRAINER"), "PERSONAL_TRAINER", "ENDED");
        link(professional("NUTRITIONIST"), "NUTRITIONIST", "ACTIVE");
        gauges.refresh();

        assertThat(value(LinkMetrics.ACTIVE_LINKS, "PERSONAL") - links).isEqualTo(2);
        assertThat(value(LinkMetrics.PROFESSIONALS_WITH_ACTIVE_LINKS, "PERSONAL") - personals)
                .isEqualTo(1);
        assertThat(value(LinkMetrics.ACTIVE_LINKS, "NUTRITIONIST") - nutritionLinks)
                .isEqualTo(1);
        assertThat(value(LinkMetrics.PROFESSIONALS_WITH_ACTIVE_LINKS, "NUTRITIONIST") - nutritionists)
                .isEqualTo(1);
    }

    @Test
    void theMetricsOnlyCarryTheTypeTag() {
        for (String name : new String[] {LinkMetrics.ACTIVE_LINKS, LinkMetrics.PROFESSIONALS_WITH_ACTIVE_LINKS}) {
            assertThat(registry.find(name).meters())
                    .hasSize(2)
                    .allSatisfy(meter -> assertThat(meter.getId().getTags())
                            .extracting(tag -> tag.getKey())
                            .containsExactly("type"));
        }
    }

    private double value(String name, String type) {
        return registry.get(name).tag("type", type).gauge().value();
    }

    private UUID professional(String type) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into professional (id, professional_type, name, invite_code, registered_at, updated_at)"
                        + " values (?, ?, 'Profissional de teste', ?, now(), now())")
                .params(id, type, inviteCode())
                .update();
        return id;
    }

    private void link(UUID professional, String type, String status) {
        boolean ended = status.equals("ENDED");
        jdbc.sql("insert into client_link (id, client_id, professional_id, professional_type, status, started_at,"
                        + " ended_at, ended_by) values (?, ?, ?, ?, ?, now() - interval '10 days', "
                        + (ended ? "now(), 'CLIENT'" : "null, null")
                        + ")")
                .params(UUID.randomUUID(), UUID.randomUUID(), professional, type, status)
                .update();
    }

    private static String inviteCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            code.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return code.toString();
    }
}
