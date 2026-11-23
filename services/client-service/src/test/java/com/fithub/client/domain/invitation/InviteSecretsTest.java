package com.fithub.client.domain.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InviteSecretsTest {

    @Test
    void codigoTem8CaracteresSemLetrasOuNumerosAmbiguos() {
        for (int i = 0; i < 500; i++) {
            String code = InviteSecrets.newCode();
            assertThat(code).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}").doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void tokenTem256BitsEmBase64Url() {
        String token = InviteSecrets.newToken();

        // 32 bytes em base64url sem preenchimento = 43 caracteres.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void segredosNaoSeRepetem() {
        Set<String> tokens = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(InviteSecrets.newToken());
            codes.add(InviteSecrets.newCode());
        }

        assertThat(tokens).hasSize(1000);
        assertThat(codes).hasSize(1000);
    }

    @Test
    void hashESha256EmHexadecimalENaoContemOSegredo() {
        String hash = InviteSecrets.hash("ABCD2345");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("ABCD2345");
        assertThat(InviteSecrets.hash("ABCD2345")).isEqualTo(hash);
        assertThat(InviteSecrets.hash("ABCD2346")).isNotEqualTo(hash);
    }

    @Test
    void codigoDigitadoComEspacoHifenOuMinusculaENormalizado() {
        assertThat(InviteSecrets.normalizeCode(" abcd-2345 ")).contains("ABCD2345");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ABCD234", "ABCD23456", "ABCD2340", "ABCDO345", "ABCD2I45"})
    void textoQueNaoPodeSerCodigoENormalizadoParaVazio(String raw) {
        assertThat(InviteSecrets.normalizeCode(raw)).isEmpty();
    }
}
