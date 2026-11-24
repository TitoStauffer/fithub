package com.fithub.training.domain.restriction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras da restrição de mobilidade (DEV-0058): região obrigatória e descrição de 1 a 500 caracteres. */
class MobilityRestrictionTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID PERSONAL = UUID.randomUUID();

    private static void assertInvalid(String field, Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(
                        InvalidRestrictionException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo(field));
    }

    @Test
    void guardaADescricaoSemEspacosNasPontas() {
        MobilityRestriction restriction =
                MobilityRestriction.record(CLIENT, BodyRegion.KNEE, "  Dor ao agachar.  ", PERSONAL, NOW);

        assertThat(restriction.description()).isEqualTo("Dor ao agachar.");
        assertThat(restriction.createdAt()).isEqualTo(NOW);
        assertThat(restriction.recordedBy()).isEqualTo(PERSONAL);
    }

    @Test
    void exigeRegiaoEDescricaoDe1A500Caracteres() {
        assertInvalid("region", () -> MobilityRestriction.record(CLIENT, null, "Dor.", PERSONAL, NOW));
        assertInvalid("description", () -> MobilityRestriction.record(CLIENT, BodyRegion.KNEE, " ", PERSONAL, NOW));
        assertInvalid("description", () -> MobilityRestriction.record(CLIENT, BodyRegion.KNEE, null, PERSONAL, NOW));
        assertInvalid(
                "description",
                () -> MobilityRestriction.record(CLIENT, BodyRegion.KNEE, "a".repeat(501), PERSONAL, NOW));
        assertThat(MobilityRestriction.record(CLIENT, BodyRegion.KNEE, "a".repeat(500), PERSONAL, NOW)
                        .description())
                .hasSize(500);
    }

    @Test
    void editarMantemOAlunoEACriacaoETrocaQuemRegistrou() {
        MobilityRestriction original = MobilityRestriction.record(CLIENT, BodyRegion.KNEE, "Dor.", PERSONAL, NOW);
        UUID next = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);

        MobilityRestriction changed = original.change(BodyRegion.HIP, "Quadril.", next, later);

        assertThat(changed.id()).isEqualTo(original.id());
        assertThat(changed.clientId()).isEqualTo(CLIENT);
        assertThat(changed.createdAt()).isEqualTo(NOW);
        assertThat(changed.updatedAt()).isEqualTo(later);
        assertThat(changed.recordedBy()).isEqualTo(next);
        assertInvalid("description", () -> original.change(BodyRegion.HIP, "", next, later));
    }
}
