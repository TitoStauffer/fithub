package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.restriction.BodyRegion;
import com.fithub.training.domain.restriction.MobilityRestriction;
import com.fithub.training.domain.restriction.RestrictionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Restrições de mobilidade no Postgres do training-service (migration V7). */
@Repository
class JdbcRestrictionRepository implements RestrictionRepository {

    private static final String SELECT = """
            select id, client_id, region, description, recorded_by, created_at, updated_at
            from mobility_restriction
            """;

    private final JdbcClient jdbc;

    JdbcRestrictionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(MobilityRestriction restriction) {
        jdbc.sql("""
                        insert into mobility_restriction (id, client_id, region, description, recorded_by, created_at,
                            updated_at)
                        values (:id, :clientId, :region, :description, :recordedBy, :createdAt, :updatedAt)
                        on conflict (id) do update set
                            region = excluded.region,
                            description = excluded.description,
                            recorded_by = excluded.recorded_by,
                            updated_at = excluded.updated_at
                        """)
                .param("id", restriction.id())
                .param("clientId", restriction.clientId())
                .param("region", restriction.region().name())
                .param("description", restriction.description())
                .param("recordedBy", restriction.recordedBy())
                .param("createdAt", Timestamp.from(restriction.createdAt()))
                .param("updatedAt", Timestamp.from(restriction.updatedAt()))
                .update();
    }

    @Override
    public Optional<MobilityRestriction> findById(UUID id) {
        return jdbc.sql(SELECT + "where id = :id")
                .param("id", id)
                .query(JdbcRestrictionRepository::toRestriction)
                .optional();
    }

    @Override
    public List<MobilityRestriction> findByClient(UUID clientId) {
        return jdbc.sql(SELECT + "where client_id = :clientId order by created_at, id")
                .param("clientId", clientId)
                .query(JdbcRestrictionRepository::toRestriction)
                .list();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from mobility_restriction where id = :id")
                .param("id", id)
                .update();
    }

    private static MobilityRestriction toRestriction(ResultSet row, int index) throws SQLException {
        return new MobilityRestriction(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                BodyRegion.valueOf(row.getString("region")),
                row.getString("description"),
                row.getObject("recorded_by", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
