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
import org.springframework.stereotype.Service;

/**
 * Fotos de evolução das avaliações (DEV-0048, FR-20). O app envia o arquivo direto ao bucket por uma URL
 * pré-assinada e depois confirma; a confirmação confere tipo e tamanho no próprio objeto. Só o cliente envia e
 * exclui; ele e os profissionais vinculados veem.
 */
@Service
public class ManagePhotos {

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
        URI url = storage.presignUpload(objectKey(assessment, pose, uploadId), type);
        return new Upload(url, uploadId, clock.instant().plus(PhotoStorage.URL_VALIDITY));
    }

    /**
     * Confirma o envio: o objeto precisa existir, ser JPEG ou PNG e ter até 10 MB; fora disso, é apagado e a
     * confirmação é recusada. A foto anterior da pose é substituída.
     */
    public Viewable confirm(String clientAccount, UUID assessmentId, Photo.Pose pose, String uploadId) {
        Assessment assessment = ownAssessment(clientAccount, assessmentId);
        String key = objectKey(assessment, pose, requireUploadId(uploadId));
        PhotoStorage.StoredObject stored = storage.describe(key)
                .orElseThrow(() -> new InvalidAssessmentException("uploadId", "Envie a foto antes de confirmar."));
        if (!Photo.CONTENT_TYPES.contains(stored.contentType()) || stored.sizeBytes() > Photo.MAX_BYTES) {
            storage.delete(key);
            throw new InvalidAssessmentException("photo", "A foto precisa ser JPEG ou PNG de até 10 MB.");
        }
        photos.find(assessmentId, pose)
                .filter(previous -> !previous.objectKey().equals(key))
                .ifPresent(previous -> storage.delete(previous.objectKey()));
        Photo photo = new Photo(assessmentId, assessment.clientId(), pose, key, clock.instant());
        photos.save(photo);
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

    public void delete(String clientAccount, UUID assessmentId, Photo.Pose pose) {
        ownAssessment(clientAccount, assessmentId);
        photos.find(assessmentId, pose).ifPresent(photo -> {
            storage.delete(photo.objectKey());
            photos.delete(assessmentId, pose);
        });
    }

    /** Apaga os arquivos das fotos de uma avaliação que está sendo excluída. */
    void deleteFilesOf(UUID assessmentId) {
        photos.listByAssessment(assessmentId).forEach(photo -> storage.delete(photo.objectKey()));
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

    private static String objectKey(Assessment assessment, Photo.Pose pose, String uploadId) {
        return "clients/%s/assessments/%s/%s-%s"
                .formatted(assessment.clientId(), assessment.id(), pose.name().toLowerCase(Locale.ROOT), uploadId);
    }
}
