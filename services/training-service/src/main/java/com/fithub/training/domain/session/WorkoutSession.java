package com.fithub.training.domain.session;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão de treino do aluno (DEV-0065): a divisão da ficha que ele fez, na versão que estava ativa (BR-09). Finalizar
 * avança a sequência (FR-29).
 *
 * @param id gerado pelo app, para iniciar de novo sem duplicar
 * @param clientId o {@code sub} do aluno (ADR-009)
 * @param finishedAt quando o aluno finalizou, ou {@code null} enquanto está aberta
 * @param comment como foi o treino, até 500 caracteres, ou {@code null} (DEV-0067)
 * @param feeling sensação de 1 (muito fácil) a 5 (exaustivo), ou {@code null} (DEV-0067)
 */
public record WorkoutSession(
        UUID id,
        UUID clientId,
        UUID planId,
        UUID versionId,
        int divisionPosition,
        Instant startedAt,
        Instant finishedAt,
        String comment,
        Integer feeling) {

    public static final int MAX_COMMENT_LENGTH = 500;

    public WorkoutSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(startedAt, "startedAt");
        if (divisionPosition < 0) {
            throw new IllegalArgumentException("A divisão começa em 0.");
        }
        comment = comment == null || comment.isBlank() ? null : comment.strip();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidSetException("comment", "O comentário pode ter até 500 caracteres.");
        }
        if (feeling != null && (feeling < 1 || feeling > 5)) {
            throw new InvalidSetException("feeling", "A sensação vai de 1 (muito fácil) a 5 (exaustivo).");
        }
    }

    /** Sessão sem comentário nem sensação. */
    public WorkoutSession(
            UUID id,
            UUID clientId,
            UUID planId,
            UUID versionId,
            int divisionPosition,
            Instant startedAt,
            Instant finishedAt) {
        this(id, clientId, planId, versionId, divisionPosition, startedAt, finishedAt, null, null);
    }

    /** A mesma sessão com o comentário e a sensação do aluno (DEV-0067, FR-49). */
    public WorkoutSession withFeedback(String newComment, Integer newFeeling) {
        return new WorkoutSession(
                id, clientId, planId, versionId, divisionPosition, startedAt, finishedAt, newComment, newFeeling);
    }

    public boolean finished() {
        return finishedAt != null;
    }

    public boolean ownedBy(UUID client) {
        return clientId.equals(client);
    }

    /**
     * Se os registros ainda podem ser corrigidos: até o fim do dia em que a sessão começou, no fuso informado
     * (BR-08, QUESTÃO-04). Vale também para a sessão finalizada.
     */
    public boolean correctable(Instant now, ZoneId zone) {
        return startedAt.atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate());
    }

    /** Finaliza; finalizar de novo não muda nada. */
    public WorkoutSession finish(Instant now) {
        if (finished()) {
            return this;
        }
        return new WorkoutSession(id, clientId, planId, versionId, divisionPosition, startedAt, now, comment, feeling);
    }

    /**
     * A divisão do próximo treino: a seguinte à da última sessão finalizada, em ordem e voltando ao início (A→B→C→A).
     * Se a versão nova tiver menos divisões, volta ao começo pelo resto.
     *
     * @param lastFinished a última sessão finalizada da ficha, ou {@code null} se ainda não houve
     */
    public static int nextDivision(WorkoutSession lastFinished, int divisions) {
        if (divisions < 1) {
            throw new IllegalArgumentException("A ficha tem ao menos uma divisão.");
        }
        return lastFinished == null ? 0 : (lastFinished.divisionPosition() + 1) % divisions;
    }
}
