package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.restriction.FoodRestriction;
import com.fithub.nutrition.domain.restriction.FoodRestrictionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Restrições alimentares no Postgres do nutrition-service (migration V7). */
@Repository
class JdbcFoodRestrictionRepository implements FoodRestrictionRepository {

    private static final String SELECT = """
            select id, client_id, kind, description, terms, recorded_by, created_at, updated_at
            from food_restriction
            """;

    private final JdbcClient jdbc;

    JdbcFoodRestrictionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FoodRestriction restriction) {
        jdbc.sql("""
                        insert into food_restriction
                            (id, client_id, kind, description, terms, recorded_by, created_at, updated_at)
                        values (:id, :clientId, :kind, :description, cast(:terms as text[]), :recordedBy, :createdAt,
                            :updatedAt)
                        on conflict (id) do update set
                            kind = excluded.kind,
                            description = excluded.description,
                            terms = excluded.terms,
                            recorded_by = excluded.recorded_by,
                            updated_at = excluded.updated_at
                        """)
                .param("id", restriction.id())
                .param("clientId", restriction.clientId())
                .param("kind", restriction.kind().name())
                .param("description", restriction.description())
                .param("terms", restriction.terms().toArray(String[]::new))
                .param("recordedBy", restriction.recordedBy())
                .param("createdAt", Timestamp.from(restriction.createdAt()))
                .param("updatedAt", Timestamp.from(restriction.updatedAt()))
                .update();
    }

    @Override
    public Optional<FoodRestriction> findById(UUID id) {
        return jdbc.sql(SELECT + "where id = :id")
                .param("id", id)
                .query(JdbcFoodRestrictionRepository::toRestriction)
                .optional();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from food_restriction where id = :id").param("id", id).update();
    }

    @Override
    public List<FoodRestriction> findByClient(UUID clientId) {
        return jdbc.sql(SELECT + "where client_id = :clientId order by created_at, id")
                .param("clientId", clientId)
                .query(JdbcFoodRestrictionRepository::toRestriction)
                .list();
    }

    private static FoodRestriction toRestriction(ResultSet row, int index) throws SQLException {
        return new FoodRestriction(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                FoodRestriction.Kind.valueOf(row.getString("kind")),
                row.getString("description"),
                Arrays.asList((String[]) row.getArray("terms").getArray()),
                row.getObject("recorded_by", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
