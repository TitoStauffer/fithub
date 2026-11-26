package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.account.NutritionExportReader;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Ids dos planos e dos registros do cliente para a exportação (DEV-0167), sempre filtrados pelo cliente. */
@Repository
class JdbcNutritionExportReader implements NutritionExportReader {

    private final JdbcClient jdbc;

    JdbcNutritionExportReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> deliveredPlanIdsOf(UUID clientId) {
        // O rascunho da nutricionista ainda não foi entregue ao cliente.
        return jdbc.sql("""
                        select id from meal_plan
                         where client_id = :c and status <> 'DRAFT'
                         order by created_at desc, id desc
                        """).param("c", clientId).query(UUID.class).list();
    }

    @Override
    public List<UUID> logIdsOf(UUID clientId) {
        return jdbc.sql("select id from meal_log where client_id = :c order by logged_on desc, meal_position, id")
                .param("c", clientId)
                .query(UUID.class)
                .list();
    }
}
