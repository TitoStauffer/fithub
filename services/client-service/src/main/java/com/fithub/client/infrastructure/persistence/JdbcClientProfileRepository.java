package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.profile.ClientProfile;
import com.fithub.client.domain.profile.ClientProfileRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Perfis de cliente no Postgres do client-service (migration V2). */
@Repository
class JdbcClientProfileRepository implements ClientProfileRepository {

    private static final String COLUMNS =
            "account_id, name, birth_date, gender, height_cm, goal, created_at, updated_at";

    private final JdbcClient jdbc;

    JdbcClientProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ClientProfile> findByAccountId(String accountId) {
        return jdbc.sql("select " + COLUMNS + " from client_profile where account_id = :accountId")
                .param("accountId", accountId)
                .query(JdbcClientProfileRepository::map)
                .optional();
    }

    @Override
    public Optional<ClientProfile> findByAccountIdForUpdate(String accountId) {
        return jdbc.sql("select " + COLUMNS + " from client_profile where account_id = :accountId for update")
                .param("accountId", accountId)
                .query(JdbcClientProfileRepository::map)
                .optional();
    }

    @Override
    public Saved save(ClientProfile profile) {
        // Upsert numa instrução só: dois pedidos simultâneos da mesma conta não criam dois perfis, e o
        // created_at do primeiro é mantido. No Postgres, xmax = 0 na linha devolvida quer dizer que ela foi
        // inserida agora (num update, xmax guarda a transação que a alterou).
        return jdbc.sql("""
                        insert into client_profile (account_id, name, birth_date, gender, height_cm, goal, created_at,
                            updated_at)
                        values (:accountId, :name, :birthDate, :gender, :heightCm, :goal, :createdAt, :updatedAt)
                        on conflict (account_id) do update set
                            name = excluded.name,
                            birth_date = excluded.birth_date,
                            gender = excluded.gender,
                            height_cm = excluded.height_cm,
                            goal = excluded.goal,
                            updated_at = excluded.updated_at
                        returning %s, (xmax = 0) as inserted
                        """.formatted(COLUMNS))
                .param("accountId", profile.accountId())
                .param("name", profile.name())
                .param("birthDate", Date.valueOf(profile.birthDate()))
                .param("gender", profile.gender().name())
                .param("heightCm", profile.heightCm())
                .param("goal", profile.goal().name())
                .param("createdAt", Timestamp.from(profile.createdAt()))
                .param("updatedAt", Timestamp.from(profile.updatedAt()))
                .query((row, index) -> new Saved(map(row, index), row.getBoolean("inserted")))
                .single();
    }

    private static ClientProfile map(ResultSet row, int index) throws SQLException {
        return new ClientProfile(
                row.getString("account_id"),
                row.getString("name"),
                row.getDate("birth_date").toLocalDate(),
                ClientProfile.Gender.valueOf(row.getString("gender")),
                row.getInt("height_cm"),
                ClientProfile.Goal.valueOf(row.getString("goal")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }
}
