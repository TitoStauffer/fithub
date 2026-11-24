package com.fithub.training.domain.exercise;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Exercício (DEV-0053, FR-22): nome, grupos musculares, equipamento, instruções e mídia por URL. O do catálogo é
 * comum a todos os personais; o próprio só é visível para o dono (BR-13).
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: os enums de grupo muscular e de equipamento, os limites
 * de tamanho e a mídia só por URL {@code https} (o FitHub não hospeda o arquivo).
 *
 * @param ownerId o {@code sub} do personal dono (ADR-009); {@code null} no catálogo
 * @param mediaUrl vídeo ou imagem da execução, ou {@code null}
 * @param attribution origem e licença do item importado (ADR-010), ou {@code null}
 */
public record Exercise(
        UUID id,
        Kind kind,
        UUID ownerId,
        String name,
        Set<MuscleGroup> muscleGroups,
        Equipment equipment,
        String instructions,
        String mediaUrl,
        Attribution attribution,
        Instant createdAt,
        Instant updatedAt) {

    /** Catálogo comum ou exercício próprio de um personal. */
    public enum Kind {
        CATALOG,
        OWN
    }

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_INSTRUCTIONS_LENGTH = 4000;
    public static final int MAX_MEDIA_URL_LENGTH = 500;

    public Exercise {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((kind == Kind.OWN) != (ownerId != null)) {
            throw new InvalidExerciseException("ownerId", "Exercício próprio tem dono; o do catálogo não tem.");
        }
        name = name == null ? "" : name.strip();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidExerciseException("name", "O nome precisa ter de 2 a 120 caracteres.");
        }
        if (muscleGroups == null || muscleGroups.isEmpty()) {
            throw new InvalidExerciseException("muscleGroups", "Informe ao menos um grupo muscular.");
        }
        muscleGroups = Set.copyOf(muscleGroups);
        if (equipment == null) {
            throw new InvalidExerciseException("equipment", "Informe o equipamento.");
        }
        instructions = blankToNull(instructions);
        if (instructions != null && instructions.length() > MAX_INSTRUCTIONS_LENGTH) {
            throw new InvalidExerciseException("instructions", "As instruções podem ter até 4000 caracteres.");
        }
        mediaUrl = blankToNull(mediaUrl);
        if (mediaUrl != null && !isHttpsUrl(mediaUrl)) {
            throw new InvalidExerciseException(
                    "mediaUrl", "O link da mídia precisa ser um endereço https de até 500 caracteres.");
        }
    }

    /** O catálogo é de todos; o exercício próprio, só do dono (BR-13). */
    public boolean visibleTo(UUID personalId) {
        return kind == Kind.CATALOG || ownerId.equals(personalId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Só https (em minúsculas, como a CHECK do banco) com host e sem usuário: nada de http, javascript ou data num
     * link que a tela abre.
     */
    private static boolean isHttpsUrl(String value) {
        if (value.length() > MAX_MEDIA_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (URISyntaxException invalid) {
            return false;
        }
    }
}
