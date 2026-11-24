package com.fithub.training.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Conteúdo de uma versão da ficha (DEV-0059): nome, início, validade e as divisões em sequência. A versão publicada
 * nunca muda; editar a ficha publicada cria outra versão (DEV-0061, BR-09).
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: nome de 2 a 80 caracteres, validade de 1 a 52 semanas, de 1
 * a 7 divisões com a letra da posição (A a G) e nome opcional de até 60 caracteres.
 *
 * @param publishedAt quando a versão foi publicada, ou {@code null} no rascunho
 */
public record PlanVersion(
        UUID id,
        int number,
        String name,
        LocalDate startsOn,
        int validityWeeks,
        List<Division> divisions,
        Instant publishedAt,
        Instant createdAt) {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_VALIDITY_WEEKS = 52;
    public static final int MAX_DIVISIONS = 7;
    public static final int MAX_DIVISION_NAME_LENGTH = 60;

    /** Divisão da ficha: a letra da posição (A, B, C...) e um nome opcional, como "Peito e tríceps". */
    public record Division(String letter, String name) {}

    public PlanVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (number < 1) {
            throw new IllegalArgumentException("A versão começa em 1.");
        }
        name = name == null ? "" : name.strip();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidPlanException("name", "O nome da ficha precisa ter de 2 a 80 caracteres.");
        }
        if (startsOn == null) {
            throw new InvalidPlanException("startsOn", "Informe a data de início.");
        }
        if (validityWeeks < 1 || validityWeeks > MAX_VALIDITY_WEEKS) {
            throw new InvalidPlanException("validityWeeks", "A validade vai de 1 a 52 semanas.");
        }
        divisions = List.copyOf(Objects.requireNonNull(divisions, "divisions"));
    }

    /**
     * Divisões a partir dos nomes, na ordem: a letra é sempre a da posição.
     *
     * @param names um nome por divisão; vazio ou {@code null} deixa a divisão só com a letra
     */
    public static List<Division> divisionsNamed(List<String> names) {
        if (names == null || names.isEmpty() || names.size() > MAX_DIVISIONS) {
            throw new InvalidPlanException("divisions", "A ficha tem de 1 a 7 divisões.");
        }
        List<Division> divisions = new ArrayList<>();
        for (int position = 0; position < names.size(); position++) {
            String name =
                    names.get(position) == null ? null : names.get(position).strip();
            if (name != null && name.length() > MAX_DIVISION_NAME_LENGTH) {
                throw new InvalidPlanException(
                        "divisions[" + position + "].name", "O nome da divisão pode ter até 60 caracteres.");
            }
            divisions.add(new Division(
                    String.valueOf((char) ('A' + position)), name == null || name.isEmpty() ? null : name));
        }
        return divisions;
    }

    /** Último dia em que a ficha vale: o início mais a validade. */
    public LocalDate expiresOn() {
        return startsOn.plusWeeks(validityWeeks);
    }

    public boolean published() {
        return publishedAt != null;
    }
}
