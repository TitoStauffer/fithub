package com.fithub.client.domain.assessment;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    /** Apaga todos os objetos com o prefixo: os confirmados e os envios que nunca foram confirmados. */
    void deleteAll(String prefix);

    /** Objetos com o prefixo e quando foram gravados; vazio se o bucket ainda não existe. */
    List<StoredKey> list(String prefix);

    record StoredObject(String contentType, long sizeBytes) {}

    record StoredKey(String objectKey, Instant lastModified) {}
}
