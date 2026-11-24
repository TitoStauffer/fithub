package com.fithub.training.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    public static final int MAX_ITEMS = 30;

    /**
     * Divisão da ficha: a letra da posição (A, B, C...), um nome opcional, como "Peito e tríceps", e os exercícios
     * prescritos, na ordem (DEV-0060).
     */
    public record Division(String letter, String name, List<PlanItem> items) {

        public Division {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }

        /** Divisão ainda sem exercícios. */
        public Division(String letter, String name) {
            this(letter, name, List.of());
        }
    }

    /**
     * O que chega da tela para uma divisão.
     *
     * @param name nome opcional
     * @param items exercícios prescritos, na ordem; {@code null} vale como nenhum
     */
    public record DivisionDraft(String name, List<PlanItem.Draft> items) {

        /** Divisão só com o nome, sem exercícios. */
        public static DivisionDraft named(String name) {
            return new DivisionDraft(name, List.of());
        }
    }

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
     * Divisões a partir do que chegou da tela, na ordem: a letra é sempre a da posição. O erro de um campo aponta a
     * divisão e o item, como {@code divisions[1].items[0].sets}.
     */
    public static List<Division> divisions(List<DivisionDraft> drafts) {
        if (drafts == null || drafts.isEmpty() || drafts.size() > MAX_DIVISIONS) {
            throw new InvalidPlanException("divisions", "A ficha tem de 1 a 7 divisões.");
        }
        List<Division> divisions = new ArrayList<>();
        for (int position = 0; position < drafts.size(); position++) {
            DivisionDraft draft = drafts.get(position) == null ? DivisionDraft.named(null) : drafts.get(position);
            String path = "divisions[" + position + "]";
            String name = draft.name() == null ? null : draft.name().strip();
            if (name != null && name.length() > MAX_DIVISION_NAME_LENGTH) {
                throw new InvalidPlanException(path + ".name", "O nome da divisão pode ter até 60 caracteres.");
            }
            divisions.add(new Division(
                    String.valueOf((char) ('A' + position)),
                    name == null || name.isEmpty() ? null : name,
                    items(path, draft.items())));
        }
        return divisions;
    }

    private static List<PlanItem> items(String path, List<PlanItem.Draft> drafts) {
        if (drafts == null) {
            return List.of();
        }
        if (drafts.size() > MAX_ITEMS) {
            throw new InvalidPlanException(path + ".items", "Cada divisão tem até 30 exercícios.");
        }
        List<PlanItem> items = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            String itemPath = path + ".items[" + index + "]";
            if (drafts.get(index) == null) {
                throw new InvalidPlanException(itemPath + ".exerciseId", "Escolha o exercício.");
            }
            try {
                items.add(drafts.get(index).toItem());
            } catch (InvalidPlanException invalid) {
                throw new InvalidPlanException(itemPath + "." + invalid.field(), invalid.getMessage());
            }
        }
        return items;
    }

    /** Último dia em que a ficha vale: o início mais a validade. */
    public LocalDate expiresOn() {
        return startsOn.plusWeeks(validityWeeks);
    }

    /** Todos os exercícios da versão, principais e alternativos. */
    public Set<UUID> exerciseIds() {
        Set<UUID> ids = new HashSet<>();
        divisions.forEach(division -> division.items().forEach(item -> ids.addAll(item.exerciseIds())));
        return ids;
    }

    public boolean published() {
        return publishedAt != null;
    }
}
