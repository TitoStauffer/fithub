package com.fithub.client.infrastructure.metrics;

import com.fithub.commons.metrics.AggregateGauges;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Métricas de sucesso do PRD dos vínculos (DEV-0113): "clientes ativos por profissional" sai de duas contagens por tipo
 * de profissional, os vínculos ativos e os profissionais com ao menos um vínculo ativo, que o painel divide. Só a
 * contagem, sem id.
 */
@Component
class LinkMetrics {

    static final String ACTIVE_LINKS = "fithub.links.active";
    static final String PROFESSIONALS_WITH_ACTIVE_LINKS = "fithub.professionals.with.active.links";

    /** Rótulo da métrica (o mesmo do account-service) para o tipo gravado no vínculo. */
    private static final Map<String, String> TYPES =
            Map.of("PERSONAL", "PERSONAL_TRAINER", "NUTRITIONIST", "NUTRITIONIST");

    LinkMetrics(AggregateGauges gauges, JdbcClient jdbc) {
        TYPES.forEach((label, stored) -> {
            gauges.register(
                    ACTIVE_LINKS,
                    "Vínculos ativos entre cliente e profissional",
                    Tags.of("type", label),
                    () -> jdbc.sql("select count(*) from client_link where status = 'ACTIVE' and professional_type = ?")
                            .param(stored)
                            .query(Long.class)
                            .single());
            gauges.register(
                    PROFESSIONALS_WITH_ACTIVE_LINKS,
                    "Profissionais com ao menos um vínculo ativo",
                    Tags.of("type", label),
                    () -> jdbc.sql("select count(distinct professional_id) from client_link"
                                    + " where status = 'ACTIVE' and professional_type = ?")
                            .param(stored)
                            .query(Long.class)
                            .single());
        });
    }
}
