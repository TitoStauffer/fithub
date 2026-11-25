package com.fithub.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fithub.testing.service.ServiceIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A migration V5 (DEV-0099) dá o teste grátis aos profissionais cadastrados antes dela. Roda as migrations num
 * schema separado: até a V4, grava um cadastro como ele existia, e então aplica a V5.
 */
class SubscriptionMigrationTest extends ServiceIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void cadastrosAnterioresGanhamOTesteDe14Dias() {
        String schema =
                "migracao_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID account = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".organization (id, owner_account_id, created_at) values (:id, :owner, now())")
                .param("id", organization)
                .param("owner", account.toString())
                .update();
        jdbc.sql("insert into " + schema + ".professional (id, account_id, name, type, council, registration_number,"
                        + " registration_category, registration_state, situation, organization_id, role_status,"
                        + " created_at) values (:id, :account, 'Ana', 'NUTRITIONIST', 'CRN', '1234', null, 'SP',"
                        + " 'DECLARED', :organization, 'ASSIGNED', now() - interval '90 days')")
                .param("id", account)
                .param("account", account.toString())
                .param("organization", organization)
                .update();

        Instant before = Instant.now().minusSeconds(5);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Timestamp trialEndsAt = jdbc.sql(
                        "select trial_ends_at from " + schema + ".subscription where professional_id = :id")
                .param("id", account)
                .query(Timestamp.class)
                .single();
        // O teste conta a partir da migration, não do cadastro antigo.
        assertThat(trialEndsAt.toInstant()).isAfter(before.plus(Duration.ofDays(14)));
        assertThat(trialEndsAt.toInstant())
                .isBefore(Instant.now().plus(Duration.ofDays(14)).plusSeconds(60));
        String plan = jdbc.sql("select plan from " + schema + ".subscription where professional_id = :id")
                .param("id", account)
                .query(String.class)
                .single();
        assertThat(plan).isEqualTo("PROFESSIONAL_MONTHLY");
        jdbc.sql("drop schema " + schema + " cascade").update();
    }
}
