package com.fithub.account.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fithub.account.application.RemoveDeletedLogin;
import com.fithub.account.domain.login.LoginAccounts;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Lista de logins apagados (DEV-0173, ADR-012, item 4): depois de uma restauração, o expurgo periódico, que também roda
 * na subida, reapaga no Keycloak cada login da lista e apaga o marcador; sem restauração, nada é reapagado; o id sai
 * da lista depois de 30 dias. O Keycloak é um falso que registra as chamadas.
 */
@TestPropertySource(properties = "fithub.professional.role-retry.enabled=false")
class DeletedLoginReapplyTest extends ServiceIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DeletedLoginPurgeScheduler scheduler;

    @Autowired
    FakeLogins logins;

    @Autowired
    RemoveDeletedLogin removeDeletedLogin;

    @TestConfiguration
    static class Fakes {

        /** Este teste não chama a API: nenhum token é aceito. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("token inválido");
            };
        }

        @Bean
        @Primary
        ProfessionalRoles fakeRoles() {
            return (accountId, type) -> {};
        }

        @Bean
        @Primary
        FakeLogins fakeLogins() {
            return new FakeLogins();
        }
    }

    /** Registra os logins apagados; falha as primeiras {@code failuresLeft} chamadas. */
    static class FakeLogins implements LoginAccounts {

        final List<UUID> deleted = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger failuresLeft = new AtomicInteger();

        @Override
        public void delete(UUID accountId) {
            if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("Keycloak fora do ar");
            }
            deleted.add(accountId);
        }
    }

    @BeforeEach
    void reset() {
        logins.failuresLeft.set(0);
        logins.deleted.clear();
        jdbc.sql("delete from restore_pending").update();
        jdbc.sql("delete from deleted_account").update();
    }

    private UUID listed(Instant deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into deleted_account (client_id, deleted_at) values (:id, :at)")
                .param("id", id)
                .param("at", Timestamp.from(deletedAt))
                .update();
        return id;
    }

    private void restored() {
        // A restauração gravou o marcador (infra/backup/restore.sh).
        jdbc.sql("insert into restore_pending (restored_at) values (now())").update();
    }

    private int pending() {
        return jdbc.sql("select count(*) from restore_pending")
                .query(Integer.class)
                .single();
    }

    private boolean stillListed(UUID id) {
        return jdbc.sql("select count(*) from deleted_account where client_id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    @Test
    void depoisDeUmaRestauracaoOExpurgoReapagaCadaLoginDaListaEApagaOMarcador() {
        UUID first = listed(Instant.now().minus(Duration.ofDays(2)));
        UUID second = listed(Instant.now().minus(Duration.ofDays(1)));
        restored();

        scheduler.purge();

        assertThat(logins.deleted).containsExactlyInAnyOrder(first, second);
        assertThat(pending()).isZero();
        assertThat(stillListed(first)).isTrue();
        assertThat(stillListed(second)).isTrue();
    }

    @Test
    void semRestauracaoNenhumLoginEReapagado() {
        listed(Instant.now().minus(Duration.ofDays(1)));

        scheduler.purge();

        assertThat(logins.deleted).isEmpty();
    }

    @Test
    void falhaDoKeycloakMantemOMarcadorEOProximoCicloReapagaDeNovo() {
        UUID id = listed(Instant.now().minus(Duration.ofDays(1)));
        restored();
        logins.failuresLeft.set(1);

        // A primeira rodada falha no Keycloak (o agendador registra e tenta de novo no próximo ciclo): o marcador fica.
        assertThatThrownBy(scheduler::purge).isInstanceOf(IllegalStateException.class);
        assertThat(pending()).isEqualTo(1);
        scheduler.purge();

        assertThat(logins.deleted).containsExactly(id);
        assertThat(pending()).isZero();
    }

    @Test
    void expurgoTiraDaListaSoQuemFoiApagadoHaMaisDe30Dias() {
        Instant now = Instant.now();
        UUID old = listed(now.minus(Duration.ofDays(30)).minus(Duration.ofMinutes(1)));
        UUID recent = listed(now.minus(Duration.ofDays(30)).plus(Duration.ofMinutes(1)));

        scheduler.purge();

        assertThat(stillListed(old)).isFalse();
        assertThat(stillListed(recent)).isTrue();
    }

    @Test
    void gravarDeNovoOMesmoIdMantemUmaLinhaComADataMaisRecente() {
        UUID id = UUID.randomUUID();
        Instant recent = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);

        removeDeletedLogin.accountDeleted(id, recent);
        removeDeletedLogin.accountDeleted(id, recent.minus(Duration.ofDays(1)));

        assertThat(jdbc.sql("select deleted_at from deleted_account where client_id = :id")
                        .param("id", id)
                        .query(Timestamp.class)
                        .list())
                .extracting(Timestamp::toInstant)
                .containsExactly(recent);
    }
}
