package com.fithub.account.infrastructure.persistence;

import com.fithub.account.domain.professional.Organization;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalRepository;
import com.fithub.account.domain.professional.Registration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Profissionais e organizações no Postgres do account-service (migration V2). */
@Repository
class JdbcProfessionalRepository implements ProfessionalRepository {

    private static final String SELECT = """
            select id, account_id, type, council, registration_number, registration_category,
                   registration_state, situation, organization_id, role_status, created_at
              from professional
            """;

    private final JdbcClient jdbc;

    JdbcProfessionalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Professional> findByAccountId(String accountId) {
        return jdbc.sql(SELECT + " where account_id = :accountId")
                .param("accountId", accountId)
                .query(JdbcProfessionalRepository::map)
                .optional();
    }

    @Override
    public void saveNew(Professional professional, Organization organization) {
        jdbc.sql("insert into organization (id, owner_account_id, created_at) values (:id, :owner, :createdAt)")
                .param("id", organization.id())
                .param("owner", organization.ownerAccountId())
                .param("createdAt", Timestamp.from(organization.createdAt()))
                .update();
        Registration registration = professional.registration();
        try {
            jdbc.sql("""
                            insert into professional (id, account_id, type, council, registration_number,
                                registration_category, registration_state, situation, organization_id, role_status,
                                created_at)
                            values (:id, :accountId, :type, :council, :number, :category, :state, :situation,
                                :organizationId, :roleStatus, :createdAt)
                            """)
                    .param("id", professional.id())
                    .param("accountId", professional.accountId())
                    .param("type", professional.type().name())
                    .param("council", registration.council().name())
                    .param("number", registration.number())
                    .param("category", registration.category())
                    .param("state", registration.state())
                    .param("situation", professional.situation().name())
                    .param("organizationId", professional.organizationId())
                    .param("roleStatus", professional.roleStatus().name())
                    .param("createdAt", Timestamp.from(professional.createdAt()))
                    .update();
        } catch (DuplicateKeyException duplicate) {
            // uk_professional_account: outro pedido da mesma conta gravou primeiro. A transação é desfeita,
            // e a organização órfã junto.
            throw new AlreadyRegisteredException(professional.accountId());
        }
    }

    @Override
    public void markRoleAssigned(UUID professionalId) {
        jdbc.sql("update professional set role_status = 'ASSIGNED' where id = :id")
                .param("id", professionalId)
                .update();
    }

    @Override
    public List<Professional> findPendingRoles(int limit) {
        return jdbc.sql(SELECT + " where role_status = 'PENDING' order by created_at limit :limit")
                .param("limit", limit)
                .query(JdbcProfessionalRepository::map)
                .list();
    }

    private static Professional map(ResultSet row, int index) throws SQLException {
        var registration = new Registration(
                Registration.Council.valueOf(row.getString("council")),
                row.getString("registration_number"),
                row.getString("registration_category"),
                row.getString("registration_state"));
        return new Professional(
                row.getObject("id", UUID.class),
                row.getString("account_id"),
                Professional.Type.valueOf(row.getString("type")),
                registration,
                Professional.Situation.valueOf(row.getString("situation")),
                row.getObject("organization_id", UUID.class),
                Professional.RoleStatus.valueOf(row.getString("role_status")),
                row.getTimestamp("created_at").toInstant());
    }
}
