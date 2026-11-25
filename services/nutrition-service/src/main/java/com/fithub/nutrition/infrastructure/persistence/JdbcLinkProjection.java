package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.access.LinkProjection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Projeção de vínculos no Postgres do nutrition-service (migration V2). */
@Repository
class JdbcLinkProjection implements LinkProjection {

    private final JdbcClient jdbc;

    JdbcLinkProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void activate(UUID linkId, UUID clientId, UUID professionalId, Instant occurredAt) {
        // Se o vínculo já existe (ativado antes, ou encerrado antes de a ativação chegar), nada muda.
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, activated_at)
                        values (:linkId, :clientId, :professionalId, 'ACTIVE', :at)
                        on conflict (link_id) do nothing
                        """)
                .param("linkId", linkId)
                .param("clientId", clientId)
                .param("professionalId", professionalId)
                .param("at", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public void end(UUID linkId, UUID clientId, UUID professionalId, Instant occurredAt) {
        // Encerramento sempre vence: cria o vínculo já encerrado se a ativação ainda não chegou.
        jdbc.sql("""
                        insert into client_link (link_id, client_id, professional_id, status, ended_at)
                        values (:linkId, :clientId, :professionalId, 'ENDED', :at)
                        on conflict (link_id) do update set status = 'ENDED',
                            ended_at = coalesce(client_link.ended_at, excluded.ended_at)
                        """)
                .param("linkId", linkId)
                .param("clientId", clientId)
                .param("professionalId", professionalId)
                .param("at", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public boolean hasActiveLink(UUID professionalId, UUID clientId) {
        return jdbc.sql("""
                        select exists (select 1 from client_link
                                        where professional_id = :professionalId and client_id = :clientId
                                          and status = 'ACTIVE')
                        """)
                .param("professionalId", professionalId)
                .param("clientId", clientId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<UUID> activeClients(UUID professionalId) {
        return jdbc.sql("""
                        select client_id from client_link
                        where professional_id = :professionalId and status = 'ACTIVE'
                        order by activated_at
                        """)
                .param("professionalId", professionalId)
                .query(UUID.class)
                .list();
    }
}
