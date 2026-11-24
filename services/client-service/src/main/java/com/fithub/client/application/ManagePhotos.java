package com.fithub.client.application;

import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.assessment.AssessmentRepository;
import com.fithub.client.domain.assessment.InvalidAssessmentException;
import com.fithub.client.domain.assessment.Photo;
import com.fithub.client.domain.assessment.PhotoRepository;
import com.fithub.client.domain.assessment.PhotoStorage;
import com.fithub.client.domain.link.LinkRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fotos de evolução das avaliações (DEV-0048, FR-20). O app envia o arquivo direto ao bucket por uma URL
 * pré-assinada e depois confirma; a confirmação confere tipo e tamanho no próprio objeto. Só o cliente envia e
 * exclui; ele e os profissionais vinculados veem.
 */
@Service
public class ManagePhotos {

    private static final Logger log = LoggerFactory.getLogger(ManagePhotos.class);

    private final AssessmentRepository assessments;
    private final PhotoRepository photos;
    private final PhotoStorage storage;
    private final LinkRepository links;
    private final Clock clock;

    public ManagePhotos(
            AssessmentRepository assessments,
            PhotoRepository photos,
            PhotoStorage storage,
            LinkRepository links,
            Clock clock) {
        this.assessments = assessments;
        this.photos = photos;
        this.storage = storage;
        this.links = links;
        this.clock = clock;
    }

    /** URL para enviar a foto e o id do envio, que a confirmação repete. */
    public record Upload(URI uploadUrl, String uploadId, Instant expiresAt) {}

    /** Foto com a URL temporária de leitura. */
    public record Viewable(Photo.Pose pose, URI url, Instant expiresAt, Instant uploadedAt) {}

    public Upload requestUpload(String clientAccount, UUID assessmentId, Photo.Pose pose, String contentType) {
        Assessment assessment = ownAssessment(clientAccount, assessmentId);
        String type = Photo.requireContentType(contentType);
        String uploadId = UUID.randomUUID().toString();
        URI url = storage.presignUpload(uploadKey(assessment, pose, uploadId), type);
        return new Upload(url, uploadId, clock.instant().plus(PhotoStorage.URL_VALIDITY));
    }

    /**
     * Confirma o envio. O objeto é copiado, dentro do bucket, para uma chave final que nenhuma URL de envio
     * alcança: a URL de `PUT` vale 5 minutos e pode ser usada de novo, então a foto guardada não pode ficar na
     * chave do envio. A cópia precisa ser JPEG ou PNG de até 10 MB; fora disso, é apagada e a confirmação é
     * recusada. A foto nova é gravada antes de qualquer arquivo antigo sair; depois, os outros objetos da pose (a
     * foto anterior e envios que nunca foram confirmados) são apagados.
     */
    public Viewable confirm(String clientAccount, UUID assessmentId, Photo.Pose pose, String uploadId) {
        Assessment assessment = ownAssessment(clientAccount, assessmentId);
        String uploadKey = uploadKey(assessment, pose, requireUploadId(uploadId));
        String key = photoKey(assessment, pose);
        if (!storage.copy(uploadKey, key)) {
            throw new InvalidAssessmentException("uploadId", "Envie a foto antes de confirmar.");
        }
        storage.delete(uploadKey);
        PhotoStorage.StoredObject stored = storage.describe(key)
                .orElseThrow(() -> new InvalidAssessmentException("uploadId", "Envie a foto antes de confirmar."));
        if (!Photo.CONTENT_TYPES.contains(stored.contentType()) || stored.sizeBytes() > Photo.MAX_BYTES) {
            storage.delete(key);
            throw new InvalidAssessmentException("photo", "A foto precisa ser JPEG ou PNG de até 10 MB.");
        }
        Photo photo = new Photo(assessmentId, assessment.clientId(), pose, key, clock.instant());
        photos.save(photo);
        storage.list(posePrefix(assessment, pose)).stream()
                .map(PhotoStorage.StoredKey::objectKey)
                .filter(other -> !other.equals(key))
                .forEach(storage::delete);
        return viewable(photo);
    }

    public List<Viewable> listMine(String clientAccount, UUID assessmentId) {
        ownAssessment(clientAccount, assessmentId);
        return photos.listByAssessment(assessmentId).stream()
                .map(this::viewable)
                .toList();
    }

    public List<Viewable> listForClient(String professionalAccount, UUID clientId, UUID assessmentId) {
        if (!links.hasActiveLink(UUID.fromString(professionalAccount), clientId)) {
            throw new ManageAssessments.NotFoundException("Cliente não encontrado.");
        }
        assessments
                .findById(assessmentId)
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(() -> new ManageAssessments.NotFoundException("Avaliação não encontrada."));
        return photos.listByAssessment(assessmentId).stream()
                .map(this::viewable)
                .toList();
    }

    /** Exclui a foto da pose: o registro e todos os objetos da pose no bucket, inclusive envios não confirmados. */
    public void delete(String clientAccount, UUID assessmentId, Photo.Pose pose) {
        Assessment assessment = ownAssessment(clientAccount, assessmentId);
        photos.delete(assessmentId, pose);
        storage.deleteAll(posePrefix(assessment, pose));
    }

    /**
     * Apaga do bucket tudo da avaliação que foi excluída, pelo prefixo: as fotos confirmadas e os envios que nunca
     * foram confirmados (direito de exclusão do titular). Com o armazenamento fora do ar, a exclusão da avaliação
     * não é desfeita: os objetos ficaram sem foto registrada, e o expurgo ({@link #purgeUnconfirmed}) os apaga.
     */
    void deleteFilesOf(Assessment assessment) {
        try {
            storage.deleteAll(assessmentPrefix(assessment));
        } catch (RuntimeException failure) {
            // Só o tipo da falha: a mensagem do S3 pode trazer a chave do objeto.
            log.warn(
                    "Arquivos da avaliação excluída ficam para o expurgo ({})",
                    failure.getClass().getSimpleName());
        }
    }

    /**
     * Expurgo dos envios que nunca foram confirmados (o app falhou, fechou ou tentou de novo com outro envio): apaga
     * os objetos gravados antes de {@code cutoff} que não são de nenhuma foto confirmada. Devolve quantos saíram.
     */
    public int purgeUnconfirmed(Instant cutoff) {
        int removed = 0;
        for (PhotoStorage.StoredKey stored : storage.list(ROOT_PREFIX)) {
            if (stored.lastModified().isBefore(cutoff) && !photos.existsByObjectKey(stored.objectKey())) {
                storage.delete(stored.objectKey());
                removed++;
            }
        }
        return removed;
    }

    private Viewable viewable(Photo photo) {
        return new Viewable(
                photo.pose(),
                storage.presignDownload(photo.objectKey()),
                clock.instant().plus(PhotoStorage.URL_VALIDITY),
                photo.uploadedAt());
    }

    private Assessment ownAssessment(String clientAccount, UUID assessmentId) {
        UUID clientId = UUID.fromString(clientAccount);
        return assessments
                .findById(assessmentId)
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(() -> new ManageAssessments.NotFoundException("Avaliação não encontrada."));
    }

    private static String requireUploadId(String uploadId) {
        try {
            return UUID.fromString(uploadId == null ? "" : uploadId).toString();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidAssessmentException("uploadId", "Envio não reconhecido. Peça uma nova URL.");
        }
    }

    /** Todos os objetos das fotos ficam abaixo deste prefixo. */
    static final String ROOT_PREFIX = "clients/";

    private static String assessmentPrefix(Assessment assessment) {
        return ROOT_PREFIX + "%s/assessments/%s/".formatted(assessment.clientId(), assessment.id());
    }

    private static String posePrefix(Assessment assessment, Photo.Pose pose) {
        return assessmentPrefix(assessment) + pose.name().toLowerCase(Locale.ROOT) + "-";
    }

    /** Chave do envio: a única que a URL pré-assinada de `PUT` alcança. */
    private static String uploadKey(Assessment assessment, Photo.Pose pose, String uploadId) {
        return posePrefix(assessment, pose) + "upload-" + uploadId;
    }

    /** Chave da foto confirmada: nova a cada confirmação e fora do alcance de qualquer URL de envio. */
    private static String photoKey(Assessment assessment, Photo.Pose pose) {
        return posePrefix(assessment, pose) + "photo-" + UUID.randomUUID();
    }
}
