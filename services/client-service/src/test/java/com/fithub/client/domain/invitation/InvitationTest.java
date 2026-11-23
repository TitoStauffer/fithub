package com.fithub.client.domain.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvitationTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static Invitation invitation(String email) {
        return Invitation.create(UUID.randomUUID(), UUID.randomUUID(), email, "t".repeat(64), "c".repeat(64), NOW);
    }

    @Test
    void conviteNovoEPendenteEVenceEm7Dias() {
        Invitation invitation = invitation("  ana@exemplo.com ");

        assertThat(invitation.email()).isEqualTo("ana@exemplo.com");
        assertThat(invitation.status()).isEqualTo(Invitation.Status.PENDING);
        assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(invitation.usableAt(NOW)).isTrue();
    }

    @Test
    void venceExatamenteAoCompletar7Dias() {
        Invitation invitation = invitation(null);

        assertThat(invitation.stateAt(NOW.plus(Duration.ofDays(7)).minusSeconds(1)))
                .isEqualTo(Invitation.State.PENDING);
        assertThat(invitation.stateAt(NOW.plus(Duration.ofDays(7)))).isEqualTo(Invitation.State.EXPIRED);
        assertThat(invitation.usableAt(NOW.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void semEmailOConviteEParaCompartilhar() {
        assertThat(invitation(null).email()).isNull();
        assertThat(invitation("   ").email()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-arroba", "x"})
    void emailInvalidoERecusadoComOCampo(String email) {
        assertThatThrownBy(() -> invitation(email))
                .isInstanceOf(InvalidInvitationException.class)
                .extracting("field")
                .isEqualTo("email");
    }

    @Test
    void emailAcimaDe254CaracteresERecusado() {
        assertThatThrownBy(() -> invitation("a".repeat(250) + "@x.com")).isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    void cancelarDeixaOConviteSemValidadeECancelarDeNovoNaoMudaNada() {
        Invitation cancelled = invitation(null).cancel(NOW.plusSeconds(60));

        assertThat(cancelled.status()).isEqualTo(Invitation.Status.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(cancelled.usableAt(NOW.plusSeconds(61))).isFalse();
        assertThat(cancelled.stateAt(NOW)).isEqualTo(Invitation.State.CANCELLED);
        assertThat(cancelled.cancel(NOW.plusSeconds(120))).isSameAs(cancelled);
    }

    @Test
    void conviteAceitoNaoSeCancelaENaoValeMais() {
        Invitation pending = invitation(null);
        Invitation accepted = new Invitation(
                pending.id(),
                pending.professionalId(),
                null,
                pending.tokenHash(),
                pending.codeHash(),
                Invitation.Status.ACCEPTED,
                pending.expiresAt(),
                pending.createdAt(),
                null);

        assertThat(accepted.usableAt(NOW)).isFalse();
        assertThat(accepted.stateAt(NOW)).isEqualTo(Invitation.State.ACCEPTED);
        assertThatThrownBy(() -> accepted.cancel(NOW)).isInstanceOf(InvitationStateException.class);
    }
}
