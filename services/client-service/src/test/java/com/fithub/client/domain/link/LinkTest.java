package com.fithub.client.domain.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID PROFESSIONAL = UUID.randomUUID();

    private static Link active() {
        return Link.start(UUID.randomUUID(), CLIENT, PROFESSIONAL, ProfessionalType.PERSONAL_TRAINER, null, NOW);
    }

    @Test
    void parteDoVinculoEOClienteOuOProfissional() {
        Link link = active();

        assertThat(link.partyOf(CLIENT)).contains(Link.Party.CLIENT);
        assertThat(link.partyOf(PROFESSIONAL)).contains(Link.Party.PROFESSIONAL);
        assertThat(link.partyOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void encerrarGravaDataEAutorEEncerrarDeNovoNaoMudaNada() {
        Link ended = active().end(Link.Party.CLIENT, NOW.plusSeconds(60));

        assertThat(ended.status()).isEqualTo(Link.Status.ENDED);
        assertThat(ended.endedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(ended.endedBy()).isEqualTo(Link.Party.CLIENT);
        assertThat(ended.end(Link.Party.PROFESSIONAL, NOW.plusSeconds(120))).isSameAs(ended);
    }

    @Test
    void vinculoEncerradoSemDataOuAutorEInvalido() {
        assertThatThrownBy(() -> new Link(
                        UUID.randomUUID(),
                        CLIENT,
                        PROFESSIONAL,
                        ProfessionalType.PERSONAL_TRAINER,
                        Link.Status.ENDED,
                        null,
                        NOW,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ninguemSeVinculaASiMesmo() {
        assertThatThrownBy(
                        () -> Link.start(UUID.randomUUID(), CLIENT, CLIENT, ProfessionalType.NUTRITIONIST, null, NOW))
                .isInstanceOf(LinkRuleException.class);
    }
}
