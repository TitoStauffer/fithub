package com.fithub.client.domain.assessment;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Foto de evolução de uma avaliação (DEV-0048, FR-20): uma por pose. O arquivo fica num bucket privado e só é
 * lido por URL pré-assinada de validade curta (NFR-02).
 *
 * <p>Decisão do Agent na ausência do Tito, para revisão: JPEG ou PNG de até 10 MB, uma foto por pose; enviar de
 * novo substitui.
 *
 * @param objectKey chave do objeto no bucket; nunca vai para log nem para a resposta
 */
public record Photo(UUID assessmentId, UUID clientId, Pose pose, String objectKey, Instant uploadedAt) {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    public enum Pose {
        FRONT,
        SIDE,
        BACK
    }

    public Photo {
        Objects.requireNonNull(assessmentId, "assessmentId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
    }

    /** Tipo de arquivo aceito para a foto. */
    public static String requireContentType(String contentType) {
        if (contentType == null || !CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAssessmentException("contentType", "A foto precisa ser JPEG ou PNG.");
        }
        return contentType;
    }
}
