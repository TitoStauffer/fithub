package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.log.InvalidMealLogException;
import com.fithub.nutrition.domain.log.MealLog;
import com.fithub.nutrition.domain.log.MealLogNotFoundException;
import com.fithub.nutrition.domain.log.MealLogRepository;
import com.fithub.nutrition.domain.log.MealPhotoStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Foto do prato no registro da refeição (DEV-0088, FR-51), no mesmo desenho das fotos de evolução (DEV-0048). O app
 * envia o arquivo direto ao bucket por uma URL pré-assinada e depois confirma; a confirmação confere tipo e tamanho no
 * próprio objeto. Só o paciente, só no dia do registro (BR-08).
 */
@Service
public class MealLogPhotos {

    private static final Logger log = LoggerFactory.getLogger(MealLogPhotos.class);

    /** Raiz das chaves no bucket: {@code meal-logs/{paciente}/{registro}/...}. */
    static final String ROOT_PREFIX = "meal-logs/";

    private final MealLogRepository logs;
    private final MealPhotoStorage storage;
    private final Clock clock;

    public MealLogPhotos(MealLogRepository logs, MealPhotoStorage storage, Clock clock) {
        this.logs = logs;
        this.storage = storage;
        this.clock = clock;
    }

    /** URL para enviar a foto e o id do envio, que a confirmação repete. */
    public record Upload(URI uploadUrl, String uploadId, Instant expiresAt) {}

    /** O registro com a URL temporária de leitura da foto, quando ela existe. */
    public record LogView(MealLog log, URI photoUrl, Instant photoUrlExpiresAt) {}

    public Upload requestUpload(String clientAccount, UUID logId, String contentType) {
        MealLog meal = own(clientAccount, logId);
        meal.requireCorrectableOn(today());
        String type = MealLog.requirePhotoContentType(contentType);
        String uploadId = UUID.randomUUID().toString();
        URI url = storage.presignUpload(prefix(meal) + "uploads/" + uploadId, type);
        return new Upload(url, uploadId, clock.instant().plus(MealPhotoStorage.URL_VALIDITY));
    }

    /**
     * Confirma o envio. O objeto é copiado, dentro do bucket, para uma chave final que nenhuma URL de envio alcança (a
     * URL de envio pode ser usada de novo enquanto vale). A cópia precisa ser JPEG ou PNG de até 10 MB; fora disso, é
     * apagada e a confirmação é recusada. A foto nova é gravada antes de o arquivo antigo sair; depois, os outros
     * objetos do registro (a foto anterior e envios não confirmados) são apagados.
     */
    public LogView confirm(String clientAccount, UUID logId, String uploadId) {
        MealLog meal = own(clientAccount, logId);
        String upload = requireUploadId(uploadId);
        String uploadKey = prefix(meal) + "uploads/" + upload;
        String key = prefix(meal) + "photo-" + upload;
        Instant now = clock.instant();
        // O prazo do dia é conferido antes de mexer no bucket: depois do dia, nada é copiado.
        MealLog withPhoto = meal.withPhoto(new MealLog.Photo(key, now), today(), now);
        // Sem o envio, a cópia não acontece e a leitura abaixo não acha o objeto.
        storage.copy(uploadKey, key);
        storage.delete(uploadKey);
        MealPhotoStorage.StoredObject stored = storage.describe(key)
                .orElseThrow(() -> new InvalidMealLogException("uploadId", "Envie a foto antes de confirmar."));
        if (!MealLog.PHOTO_CONTENT_TYPES.contains(stored.contentType())
                || stored.sizeBytes() > MealLog.MAX_PHOTO_BYTES) {
            storage.delete(key);
            throw new InvalidMealLogException("photo", "A foto precisa ser JPEG ou PNG de até 10 MB.");
        }
        logs.savePhoto(withPhoto);
        deleteOthers(meal, key);
        return view(withPhoto);
    }

    /** Tira a foto do registro e apaga os arquivos dele no bucket. */
    public LogView remove(String clientAccount, UUID logId) {
        MealLog meal = own(clientAccount, logId);
        MealLog without = meal.withoutPhoto(today(), clock.instant());
        logs.savePhoto(without);
        deleteOthers(meal, null);
        return view(without);
    }

    /** O registro com a URL de leitura da foto (5 minutos). */
    public LogView view(MealLog meal) {
        if (meal.photo() == null) {
            return new LogView(meal, null, null);
        }
        return new LogView(
                meal,
                storage.presignDownload(meal.photo().objectKey()),
                clock.instant().plus(MealPhotoStorage.URL_VALIDITY));
    }

    /**
     * Apaga os arquivos do registro que foi excluído. Com o armazenamento fora do ar, a exclusão do registro não é
     * desfeita: os objetos ficam sem registro, e o expurgo os leva.
     */
    void deleteFilesOf(MealLog meal) {
        try {
            deleteOthers(meal, null);
        } catch (RuntimeException failure) {
            // Só o tipo da falha: a mensagem do S3 pode trazer a chave do objeto.
            log.warn(
                    "Arquivos do registro excluído ficam para o expurgo ({})",
                    failure.getClass().getSimpleName());
        }
    }

    /**
     * Apaga do bucket as fotos do prato do cliente que excluiu a conta (DEV-0163), pelo prefixo dele. Com o
     * armazenamento fora do ar, a exclusão não é desfeita: nenhum registro aponta mais para os objetos, e o expurgo
     * ({@link #purgeUnconfirmed}) os leva.
     */
    public void deleteFilesOfClient(UUID clientId) {
        try {
            storage.list(ROOT_PREFIX + clientId + "/").stream()
                    .map(MealPhotoStorage.StoredKey::objectKey)
                    .forEach(storage::delete);
        } catch (RuntimeException failure) {
            // Só o tipo da falha: a mensagem do S3 pode trazer a chave do objeto.
            log.warn(
                    "Fotos da conta excluída ficam para o expurgo ({})",
                    failure.getClass().getSimpleName());
        }
    }

    /**
     * Expurgo dos envios que nunca foram confirmados: apaga os objetos gravados antes de {@code cutoff} que não são
     * a foto de nenhum registro. Devolve quantos saíram.
     */
    public int purgeUnconfirmed(Instant cutoff) {
        int removed = 0;
        for (MealPhotoStorage.StoredKey stored : storage.list(ROOT_PREFIX)) {
            if (stored.lastModified().isBefore(cutoff) && !logs.existsByPhotoKey(stored.objectKey())) {
                storage.delete(stored.objectKey());
                removed++;
            }
        }
        return removed;
    }

    private void deleteOthers(MealLog meal, String keep) {
        storage.list(prefix(meal)).stream()
                .map(MealPhotoStorage.StoredKey::objectKey)
                .filter(other -> !other.equals(keep))
                .forEach(storage::delete);
    }

    private MealLog own(String clientAccount, UUID logId) {
        UUID clientId = ViewFood.accountId(clientAccount);
        return logs.findById(logId)
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(MealLogNotFoundException::new);
    }

    private static String prefix(MealLog meal) {
        return ROOT_PREFIX + meal.clientId() + "/" + meal.id() + "/";
    }

    private static String requireUploadId(String uploadId) {
        try {
            return UUID.fromString(uploadId == null ? "" : uploadId).toString();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidMealLogException("uploadId", "Envio inválido.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(LogMeals.ZONE));
    }
}
