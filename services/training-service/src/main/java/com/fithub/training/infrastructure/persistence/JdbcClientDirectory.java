package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.access.ClientDirectory;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cópia local dos dados básicos dos clientes no Postgres do training-service (migration V2). */
@Repository
class JdbcClientDirectory implements ClientDirectory {

    private final JdbcClient jdbc;

    JdbcClientDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void update(ProjectedClient client) {
        // Só substitui se o evento for mais novo: um ClienteAtualizado atrasado não desfaz o mais recente.
        jdbc.sql("""
                        insert into client (client_id, name, birth_date, gender, updated_at)
                        values (:clientId, :name, :birthDate, :gender, :updatedAt)
                        on conflict (client_id) do update set
                            name = excluded.name,
                            birth_date = excluded.birth_date,
                            gender = excluded.gender,
                            updated_at = excluded.updated_at
                        where client.updated_at < excluded.updated_at
                        """)
                .param("clientId", client.clientId())
                .param("name", client.name())
                .param("birthDate", client.birthDate() == null ? null : Date.valueOf(client.birthDate()))
                .param("gender", client.gender())
                .param("updatedAt", Timestamp.from(client.updatedAt()))
                .update();
    }

    @Override
    public Optional<ProjectedClient> find(UUID clientId) {
        return jdbc.sql("select client_id, name, birth_date, gender, updated_at from client where client_id = :id")
                .param("id", clientId)
                .query((row, index) -> {
                    Date birthDate = row.getDate("birth_date");
                    return new ProjectedClient(
                            row.getObject("client_id", UUID.class),
                            row.getString("name"),
                            birthDate == null ? null : birthDate.toLocalDate(),
                            row.getString("gender"),
                            row.getTimestamp("updated_at").toInstant());
                })
                .optional();
    }
}
