package com.fithub.account.domain.professional;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registro no conselho profissional (FR-04). No MVP o registro é só declarado (decisão do Tito,
 * QUESTÃO-08): o formato é validado, a existência no conselho não.
 *
 * <p>Formato aceito (decisão do Agent na ausência do Tito, registrada no PRD): número de 1 a 6 dígitos
 * e a UF onde o registro foi emitido. Para o CREF, a categoria (G, graduado, ou P, provisionado) também
 * é obrigatória. O CREF é exibido como {@code CREF 012345-G/SP} e o CRN como {@code CRN 12345/SP}.
 *
 * @param council conselho, que decorre do tipo de profissional
 * @param number só dígitos, sem zeros à esquerda removidos (é como o conselho emite)
 * @param category {@code G} ou {@code P} no CREF; {@code null} no CRN
 * @param state UF de emissão, em maiúsculas
 */
public record Registration(Council council, String number, String category, String state) {

    /** Conselho que habilita cada tipo de profissional (PRD §3). */
    public enum Council {
        CREF,
        CRN
    }

    private static final Pattern NUMBER = Pattern.compile("\\d{1,6}");
    private static final Set<String> CREF_CATEGORIES = Set.of("G", "P");
    private static final Set<String> STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public Registration {
        if (council == null) {
            throw new InvalidRegistrationException("council", "Informe o conselho.");
        }
        if (number == null || !NUMBER.matcher(number).matches()) {
            throw new InvalidRegistrationException("registrationNumber", "Número do registro: de 1 a 6 dígitos.");
        }
        if (state == null || !STATES.contains(state)) {
            throw new InvalidRegistrationException("registrationState", "UF do registro inválida.");
        }
        if (council == Council.CREF && (category == null || !CREF_CATEGORIES.contains(category))) {
            throw new InvalidRegistrationException("registrationCategory", "Categoria do CREF: G ou P.");
        }
        if (council == Council.CRN && category != null) {
            throw new InvalidRegistrationException("registrationCategory", "O CRN não tem categoria.");
        }
    }

    /**
     * Monta o registro a partir do que a pessoa digitou: tira espaços e põe UF e categoria em
     * maiúsculas. Categoria em branco conta como ausente.
     */
    public static Registration of(Council council, String number, String category, String state) {
        return new Registration(council, trim(number), upper(category), upper(state));
    }

    /** Como o conselho escreve o registro, para exibição. */
    public String display() {
        return council == Council.CREF
                ? "CREF %s-%s/%s".formatted(number, category, state)
                : "CRN %s/%s".formatted(number, state);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
