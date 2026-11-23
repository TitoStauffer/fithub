package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.invitation.InviteSecrets;
import com.fithub.client.domain.professional.Professional;
import com.fithub.client.domain.professional.ProfessionalDirectory;
import com.fithub.client.domain.professional.ProfessionalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Projeção dos profissionais no Postgres do client-service (migration V4). */
@Repository
class JdbcProfessionalDirectory implements ProfessionalDirectory {

    private static final String COLUMNS = "id, professional_type, name, invite_code";
    private static final int MAX_ATTEMPTS = 3;

    private final JdbcClient jdbc;

    JdbcProfessionalDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Professional> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from professional where id = :id")
                .param("id", id)
                .query(JdbcProfessionalDirectory::map)
                .optional();
    }

    @Override
    public Optional<Professional> findByInviteCode(String code) {
        return jdbc.sql("select " + COLUMNS + " from professional where invite_code = :code")
                .param("code", code)
                .query(JdbcProfessionalDirectory::map)
                .optional();
    }

    @Override
    public void register(UUID id, ProfessionalType type, String name, Instant registeredAt) {
        // O código só vale para a primeira gravação: repetir o profissional mantém o código que ele já tem.
        // Roda dentro da transação do consumidor: uma colisão de código (1 em 10^12) falha a transação, e a
        // reentrega do evento tenta de novo com outro código.
        jdbc.sql("""
                        insert into professional (id, professional_type, name, invite_code, registered_at, updated_at)
                        values (:id, :type, :name, :code, :at, :at)
                        on conflict (id) do update set
                            professional_type = excluded.professional_type,
                            name = excluded.name,
                            updated_at = excluded.updated_at
                        """)
                .param("id", id)
                .param("type", type.name())
                .param("name", name)
                .param("code", InviteSecrets.newCode())
                .param("at", Timestamp.from(registeredAt))
                .update();
    }

    @Override
    public String rotateInviteCode(UUID id, Instant now) {
        // Fora de transação (cada update se confirma sozinho): a colisão de código pode ser tentada de novo.
        String[] code = new String[1];
        retryOnCodeCollision(() -> {
            code[0] = InviteSecrets.newCode();
            return jdbc.sql("update professional set invite_code = :code, updated_at = :now where id = :id")
                    .param("code", code[0])
                    .param("now", Timestamp.from(now))
                    .param("id", id)
                    .update();
        });
        return code[0];
    }

    private static void retryOnCodeCollision(java.util.function.IntSupplier write) {
        for (int attempt = 1; ; attempt++) {
            try {
                write.getAsInt();
                return;
            } catch (DuplicateKeyException collision) {
                if (attempt == MAX_ATTEMPTS) {
                    throw collision;
                }
            }
        }
    }

    private static Professional map(ResultSet row, int index) throws SQLException {
        return new Professional(
                row.getObject("id", UUID.class),
                ProfessionalType.valueOf(row.getString("professional_type")),
                row.getString("name"),
                row.getString("invite_code"));
    }
}
