package com.fithub.client.application;

import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.assessment.AssessmentRepository;
import com.fithub.client.domain.assessment.BodySeries;
import com.fithub.client.domain.assessment.Circumferences;
import com.fithub.client.domain.assessment.Skinfolds;
import com.fithub.client.domain.link.LinkRepository;
import com.fithub.client.domain.profile.ClientProfileRepository;
import com.fithub.commons.ids.UuidV7;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Avaliações corporais (DEV-0044): o cliente registra as online e o profissional vinculado as presenciais; o
 * cliente e os profissionais vinculados veem o histórico; só o cliente exclui.
 */
@Service
public class ManageAssessments {

    private final AssessmentRepository assessments;
    private final LinkRepository links;
    private final ClientProfileRepository profiles;
    private final ManagePhotos photos;
    private final Clock clock;

    public ManageAssessments(
            AssessmentRepository assessments,
            LinkRepository links,
            ClientProfileRepository profiles,
            ManagePhotos photos,
            Clock clock) {
        this.assessments = assessments;
        this.links = links;
        this.profiles = profiles;
        this.photos = photos;
        this.clock = clock;
    }

    /** O que foi medido. Quem mede e o tipo vêm do token e da rota, nunca do corpo. */
    public record Command(
            LocalDate measuredOn,
            BigDecimal weightKg,
            Circumferences circumferences,
            Assessment.Sex sexForFormulas,
            Skinfolds skinfolds,
            BigDecimal bioimpedanceBodyFatPercent) {}

    /** Cliente sem vínculo ativo com o profissional, ou avaliação que não é da conta (404). */
    public static final class NotFoundException extends RuntimeException {

        public NotFoundException(String message) {
            super(message);
        }
    }

    public Assessment recordOnline(String clientAccount, Command command) {
        UUID clientId = UUID.fromString(clientAccount);
        return save(clientId, Assessment.Type.ONLINE, command, clientId, Assessment.AuthorRole.CLIENT);
    }

    public Assessment recordPresential(String professionalAccount, UUID clientId, Command command) {
        UUID professionalId = requireActiveLink(professionalAccount, clientId);
        return save(clientId, Assessment.Type.PRESENTIAL, command, professionalId, Assessment.AuthorRole.PROFESSIONAL);
    }

    public List<Assessment> listMine(String clientAccount) {
        return assessments.listByClient(UUID.fromString(clientAccount));
    }

    public List<Assessment> listForClient(String professionalAccount, UUID clientId) {
        requireActiveLink(professionalAccount, clientId);
        return assessments.listByClient(clientId);
    }

    /** A série da evolução corporal do próprio cliente (DEV-0093); sem as datas, todo o histórico. */
    public BodySeries seriesMine(String clientAccount, LocalDate from, LocalDate to) {
        return BodySeries.of(listMine(clientAccount), from, to);
    }

    /** A série do cliente vinculado, para o profissional (BR-04); sem vínculo ativo, 404 (BR-05). */
    public BodySeries seriesForClient(String professionalAccount, UUID clientId, LocalDate from, LocalDate to) {
        return BodySeries.of(listForClient(professionalAccount, clientId), from, to);
    }

    /** Só o cliente, dono dos dados, exclui; avaliação de outra pessoa é 404. */
    public void deleteMine(String clientAccount, UUID assessmentId) {
        UUID clientId = UUID.fromString(clientAccount);
        Assessment assessment = assessments
                .findById(assessmentId)
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(() -> new NotFoundException("Avaliação não encontrada."));
        // A avaliação sai primeiro (as linhas das fotos saem junto, on delete cascade). Depois, tudo dela sai do
        // bucket, inclusive envios não confirmados; se o armazenamento falhar, o expurgo apaga os objetos órfãos.
        assessments.delete(assessment.id());
        photos.deleteFilesOf(assessment);
    }

    private Assessment save(
            UUID clientId, Assessment.Type type, Command command, UUID authorId, Assessment.AuthorRole role) {
        Assessment assessment = Assessment.record(
                UuidV7.next(clock),
                clientId,
                type,
                command.measuredOn(),
                command.weightKg(),
                command.circumferences(),
                command.sexForFormulas(),
                command.skinfolds(),
                authorId,
                role,
                ageAt(clientId, command.measuredOn()),
                heightOf(clientId),
                command.bioimpedanceBodyFatPercent(),
                LocalDate.now(clock),
                clock.instant());
        assessments.save(assessment);
        return assessment;
    }

    /** Idade na data da medição, a partir do nascimento do perfil; sem perfil ou sem data, não há idade. */
    private Integer ageAt(UUID clientId, LocalDate measuredOn) {
        if (measuredOn == null) return null;
        return profiles.findByAccountId(clientId.toString())
                .map(profile -> Period.between(profile.birthDate(), measuredOn).getYears())
                .orElse(null);
    }

    /** Altura atual do perfil (FR-21), usada na fórmula da Marinha. */
    private Integer heightOf(UUID clientId) {
        return profiles.findByAccountId(clientId.toString())
                .map(profile -> profile.heightCm())
                .orElse(null);
    }

    private UUID requireActiveLink(String professionalAccount, UUID clientId) {
        UUID professionalId = UUID.fromString(professionalAccount);
        if (!links.hasActiveLink(professionalId, clientId)) {
            throw new NotFoundException("Cliente não encontrado.");
        }
        return professionalId;
    }
}
