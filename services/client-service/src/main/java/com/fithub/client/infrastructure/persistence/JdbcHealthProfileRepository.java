package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.profile.HealthProfile;
import com.fithub.client.domain.profile.HealthProfileRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Perfis de saúde no Postgres do client-service (migration V3). Nada daqui vai para log. */
@Repository
class JdbcHealthProfileRepository implements HealthProfileRepository {

    private static final String COLUMNS = "account_id, conditions, notes, created_at, updated_at";

    private final JdbcClient jdbc;

    JdbcHealthProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<HealthProfile> findByAccountId(String accountId) {
        return jdbc.sql("select " + COLUMNS + " from client_health where account_id = :accountId")
                .param("accountId", accountId)
                .query(JdbcHealthProfileRepository::map)
                .optional();
    }

    @Override
    public HealthProfile save(HealthProfile profile) {
        String[] conditions = profile.conditions().stream().map(Enum::name).toArray(String[]::new);
        // Upsert numa instrução só: pedidos simultâneos da mesma conta terminam com um perfil de saúde, e a
        // data de criação do primeiro é mantida.
        return jdbc.sql("""
                        insert into client_health (account_id, conditions, notes, created_at, updated_at)
                        values (:accountId, :conditions, :notes, :createdAt, :updatedAt)
                        on conflict (account_id) do update set
                            conditions = excluded.conditions,
                            notes = excluded.notes,
                            updated_at = excluded.updated_at
                        returning %s
                        """.formatted(COLUMNS))
                .param("accountId", profile.accountId())
                .param("conditions", conditions)
                .param("notes", profile.notes())
                .param("createdAt", Timestamp.from(profile.createdAt()))
                .param("updatedAt", Timestamp.from(profile.updatedAt()))
                .query(JdbcHealthProfileRepository::map)
                .single();
    }

    private static HealthProfile map(ResultSet row, int index) throws SQLException {
        String[] conditions = (String[]) row.getArray("conditions").getArray();
        return HealthProfile.of(
                row.getString("account_id"),
                Arrays.stream(conditions).map(HealthProfile.Condition::valueOf).toList(),
                row.getString("notes"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
