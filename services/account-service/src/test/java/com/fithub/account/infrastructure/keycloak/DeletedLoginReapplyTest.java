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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
// O intervalo curto depois de falha fica em 100 ms, para o teste do agendador rodando não esperar 1 minuto.
@TestPropertySource(
        properties = {"fithub.professional.role-retry.enabled=false", "fithub.deleted-accounts.retry-interval=PT0.1S"})
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

    /**
     * Registra os logins apagados; falha as primeiras {@code failuresLeft} chamadas. Com {@code hold}, a próxima chamada
     * avisa em {@code entered} e espera {@code hold} abrir (um Keycloak lento), sem parar com a interrupção do stop.
     */
    static class FakeLogins implements LoginAccounts {

        final List<UUID> deleted = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger failuresLeft = new AtomicInteger();
        final AtomicReference<CountDownLatch> hold = new AtomicReference<>();
        final CountDownLatch entered = new CountDownLatch(1);

        @Override
        public void delete(UUID accountId) {
            CountDownLatch latch = hold.getAndSet(null);
            if (latch != null) {
                entered.countDown();
                while (true) {
                    try {
                        latch.await();
                        break;
                    } catch (InterruptedException ignored) {
                        // O stop interrompe a rodada; o Keycloak lento continua até responder.
                    }
                }
            }
            if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("Keycloak fora do ar");
            }
            deleted.add(accountId);
        }
    }

    @BeforeEach
    void reset() {
        logins.failuresLeft.set(0);
        logins.hold.set(null);
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

    // DEV-0175: com o Keycloak ainda subindo depois da restauração, a próxima tentativa vem no intervalo curto (1 min
    // em
    // produção, 100 ms aqui), não em 6 h.
    // Quando a reaplicação passa (o marcador sai), o intervalo volta a ser o do expurgo.
    @Test
    void rodadaQueFalhaETentadaDeNovoEmMinutosEAQueDaCertoVoltaAoIntervaloDoExpurgo() {
        UUID id = listed(Instant.now().minus(Duration.ofDays(1)));
        restored();
        logins.failuresLeft.set(1);

        assertThat(scheduler.runOnce()).isEqualTo(Duration.ofMillis(100));
        assertThat(pending()).isEqualTo(1);

        assertThat(scheduler.runOnce()).isEqualTo(Duration.ofHours(6));
        assertThat(logins.deleted).containsExactly(id);
        assertThat(pending()).isZero();
    }

    // DEV-0175: com o agendador rodando, a rodada que falha é agendada de novo pelo intervalo curto: duas falhas do
    // Keycloak seguidas e o login sai em menos de 10 s, não em 6 h.
    @Test
    void comOAgendadorRodandoAFalhaETentadaDeNovoLogo() throws InterruptedException {
        scheduler.stop();
        UUID id = listed(Instant.now().minus(Duration.ofDays(1)));
        restored();
        logins.failuresLeft.set(2);
        try {
            scheduler.start();
            Instant deadline = Instant.now().plusSeconds(10);
            while (pending() > 0 && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertThat(pending()).isZero();
            assertThat(logins.deleted).containsExactly(id);
        } finally {
            // Parado até o fim da classe: os outros testes chamam a rodada direto, sem o agendador em paralelo.
            scheduler.stop();
        }
    }

    // DEV-0175 (Code Review do PR #351): parar e voltar o serviço enquanto uma rodada espera o Keycloak não abre uma
    // segunda cadeia de rodadas no executor novo: quando a rodada antiga termina, ela não agenda nada.
    @Test
    void pararEVoltarDuranteUmaRodadaNaoAbreUmaSegundaCadeia() throws InterruptedException {
        scheduler.stop();
        UUID id = listed(Instant.now().minus(Duration.ofDays(1)));
        restored();
        CountDownLatch release = new CountDownLatch(1);
        logins.hold.set(release);
        try {
            scheduler.start();
            assertThat(logins.entered.await(10, TimeUnit.SECONDS)).isTrue();
            scheduler.stop();
            scheduler.start();
            // A cadeia nova reapaga o login e agenda a próxima rodada, em 6 h.
            Instant deadline = Instant.now().plusSeconds(10);
            while ((pending() > 0 || scheduler.scheduledRuns() == 0)
                    && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertThat(scheduler.scheduledRuns()).isEqualTo(1);

            release.countDown();
            // A rodada antiga termina (apaga o login de novo) e não agenda mais nada.
            deadline = Instant.now().plusSeconds(2);
            while (scheduler.scheduledRuns() == 1 && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertThat(logins.deleted).containsExactly(id, id);
            assertThat(scheduler.scheduledRuns()).isEqualTo(1);
        } finally {
            release.countDown();
            scheduler.stop();
        }
    }

    // DEV-0175 (Code Review do PR #351): intervalo zero ou negativo é recusado na subida, em vez de virar repetição sem
    // espera.
    @Test
    void intervaloZeroOuNegativoERecusado() {
        assertThatThrownBy(() -> new DeletedLoginPurgeScheduler(null, Duration.ZERO, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purge-interval");
        assertThatThrownBy(() -> new DeletedLoginPurgeScheduler(null, Duration.ofHours(6), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-interval");
        assertThatThrownBy(() -> new DeletedLoginPurgeScheduler(null, Duration.ofHours(6), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-interval");
    }

    // DEV-0175: sem restauração, a rodada usa o intervalo do expurgo.
    @Test
    void semRestauracaoAProximaRodadaEDaquiASeisHoras() {
        listed(Instant.now().minus(Duration.ofDays(1)));

        assertThat(scheduler.runOnce()).isEqualTo(Duration.ofHours(6));
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
