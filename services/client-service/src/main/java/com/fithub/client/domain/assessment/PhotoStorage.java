package com.fithub.client.domain.assessment;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/** Armazenamento privado das fotos (porta do domínio, DEV-0048). Nada daqui vai para log. */
public interface PhotoStorage {

    /** Validade das URLs pré-assinadas. */
    Duration URL_VALIDITY = Duration.ofMinutes(5);

    /** URL para o app enviar o arquivo direto ao bucket, com o tipo fixado. */
    URI presignUpload(String objectKey, String contentType);

    /** URL temporária para ler o arquivo. */
    URI presignDownload(String objectKey);

    /** O que está gravado no objeto, se ele existe. */
    Optional<StoredObject> describe(String objectKey);

    void delete(String objectKey);

    record StoredObject(String contentType, long sizeBytes) {}
}
