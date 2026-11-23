package com.fithub.account.domain.professional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfessionalTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    /** sub de uma conta do Keycloak. */
    private static final String ACCOUNT = "0193a1b2-0000-7000-8000-000000000001";

    @Test
    void registroDoCrefNormalizaOQueFoiDigitadoEExibeComoOConselho() {
        Registration registration = Registration.of(Registration.Council.CREF, " 012345 ", "g", "sp");

        assertThat(registration).isEqualTo(new Registration(Registration.Council.CREF, "012345", "G", "SP"));
        assertThat(registration.display()).isEqualTo("CREF 012345-G/SP");
    }

    @Test
    void registroDoCrnNaoTemCategoria() {
        Registration registration = Registration.of(Registration.Council.CRN, "12345", " ", "RJ");

        assertThat(registration.category()).isNull();
        assertThat(registration.display()).isEqualTo("CRN 12345/RJ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12a45", "12 45", "-123", "١٢٣"})
    @NullAndEmptySource
    void numeroForaDoFormatoERecusado(String number) {
        assertThatThrownBy(() -> Registration.of(Registration.Council.CRN, number, null, "SP"))
                .isInstanceOf(InvalidRegistrationException.class)
                .extracting("field")
                .isEqualTo("registrationNumber");
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "S", "SPP", "BR"})
    @NullAndEmptySource
    void ufInexistenteERecusada(String state) {
        assertThatThrownBy(() -> Registration.of(Registration.Council.CRN, "123", null, state))
                .isInstanceOf(InvalidRegistrationException.class)
                .extracting("field")
                .isEqualTo("registrationState");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "GP", "1"})
    @NullAndEmptySource
    void crefExigeCategoriaGouP(String category) {
        assertThatThrownBy(() -> Registration.of(Registration.Council.CREF, "123", category, "SP"))
                .isInstanceOf(InvalidRegistrationException.class)
                .extracting("field")
                .isEqualTo("registrationCategory");
    }

    @Test
    void crnComCategoriaERecusado() {
        assertThatThrownBy(() -> Registration.of(Registration.Council.CRN, "123", "G", "SP"))
                .isInstanceOf(InvalidRegistrationException.class)
                .extracting("field")
                .isEqualTo("registrationCategory");
    }

    @ParameterizedTest
    @CsvSource({"PERSONAL,CRN,", "NUTRITIONIST,CREF,G"})
    void tipoERegistroPrecisamSerDoMesmoConselho(
            Professional.Type type, Registration.Council council, String category) {
        Registration registration = Registration.of(council, "123", category, "SP");

        assertThatThrownBy(
                        () -> Professional.register(ACCOUNT, "Ana Teste", type, registration, UUID.randomUUID(), NOW))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void cadastroNovoNasceDeclaradoComPapelPendente() {
        Professional professional = personal("012345", "G", "SP");

        assertThat(professional.situation()).isEqualTo(Professional.Situation.DECLARED);
        assertThat(professional.roleStatus()).isEqualTo(Professional.RoleStatus.PENDING);
        assertThat(professional.withRoleAssigned().roleStatus()).isEqualTo(Professional.RoleStatus.ASSIGNED);
    }

    @Test
    void mesmoCadastroSoComMesmoTipoEMesmoRegistro() {
        Professional professional = personal("012345", "G", "SP");

        assertThat(professional.sameAs(
                        Professional.Type.PERSONAL, Registration.of(Registration.Council.CREF, "012345", "G", "SP")))
                .isTrue();
        assertThat(professional.sameAs(
                        Professional.Type.PERSONAL, Registration.of(Registration.Council.CREF, "012345", "G", "RJ")))
                .isFalse();
        assertThat(professional.sameAs(
                        Professional.Type.NUTRITIONIST,
                        Registration.of(Registration.Council.CRN, "012345", null, "SP")))
                .isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void profissionalSemContaERecusado(String accountId) {
        Registration registration = Registration.of(Registration.Council.CREF, "1", "G", "SP");

        assertThatThrownBy(() -> Professional.register(
                        accountId, "Ana Teste", Professional.Type.PERSONAL, registration, UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nomeChegaSemEspacosNasPontas() {
        assertThat(personal("1", "G", "SP").name()).isEqualTo("Ana Teste");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cadastroSemNomeERecusadoComOCampo(String name) {
        Registration registration = Registration.of(Registration.Council.CREF, "1", "G", "SP");

        assertThatThrownBy(() -> Professional.register(
                        ACCOUNT, name, Professional.Type.PERSONAL, registration, UUID.randomUUID(), NOW))
                .isInstanceOf(InvalidRegistrationException.class)
                .extracting("field")
                .isEqualTo("name");
    }

    @Test
    void nomeAcimaDoLimiteDoContratoERecusado() {
        Registration registration = Registration.of(Registration.Council.CREF, "1", "G", "SP");
        String atLimit = "a".repeat(Professional.MAX_NAME);

        assertThat(Professional.register(
                                ACCOUNT, atLimit, Professional.Type.PERSONAL, registration, UUID.randomUUID(), NOW)
                        .name())
                .hasSize(Professional.MAX_NAME);
        assertThatThrownBy(() -> Professional.register(
                        ACCOUNT, atLimit + "a", Professional.Type.PERSONAL, registration, UUID.randomUUID(), NOW))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void idDoProfissionalEOSubDaConta() {
        // ADR-009: o mesmo id em todos os serviços, reconhecido pelo token.
        assertThat(personal("1", "G", "SP").id()).isEqualTo(UUID.fromString(ACCOUNT));
    }

    @Test
    void contaForaDoFormatoUuidERecusada() {
        Registration registration = Registration.of(Registration.Council.CREF, "1", "G", "SP");

        assertThatThrownBy(() -> Professional.register(
                        "conta-sem-uuid", "Ana", Professional.Type.PERSONAL, registration, UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void profissionalComIdDiferenteDaContaERecusado() {
        Professional professional = personal("1", "G", "SP");

        assertThatThrownBy(() -> new Professional(
                        UUID.randomUUID(),
                        professional.accountId(),
                        professional.name(),
                        professional.type(),
                        professional.registration(),
                        professional.situation(),
                        professional.organizationId(),
                        professional.roleStatus(),
                        professional.createdAt()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADR-009");
    }

    private static Professional personal(String number, String category, String state) {
        return Professional.register(
                ACCOUNT,
                "  Ana Teste  ",
                Professional.Type.PERSONAL,
                Registration.of(Registration.Council.CREF, number, category, state),
                UUID.randomUUID(),
                NOW);
    }
}
