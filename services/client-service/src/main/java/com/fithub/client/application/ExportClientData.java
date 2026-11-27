package com.fithub.client.application;

import com.fithub.client.domain.account.PrivacyRecords;
import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.link.Link;
import com.fithub.client.domain.profile.HealthConsents;
import com.fithub.client.domain.profile.HealthProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Exportação dos dados que o client-service tem do cliente (DEV-0104, NFR-01, LGPD art. 18, II e V): perfil, saúde,
 * avaliações com as fotos, vínculos com o consentimento de cada um, os aceites do termo do perfil de saúde (DEV-0179) e
 * as leituras da saúde pelos profissionais. A conta
 * vem sempre do token; o app junta esta exportação com a do training e a do nutrition.
 */
@Service
public class ExportClientData {

    private final ManageClientProfile profiles;
    private final ManageHealthProfile health;
    private final ManageAssessments assessments;
    private final ManagePhotos photos;
    private final ManageLinks links;
    private final PrivacyRecords privacy;
    private final Clock clock;

    public ExportClientData(
            ManageClientProfile profiles,
            ManageHealthProfile health,
            ManageAssessments assessments,
            ManagePhotos photos,
            ManageLinks links,
            PrivacyRecords privacy,
            Clock clock) {
        this.profiles = profiles;
        this.health = health;
        this.assessments = assessments;
        this.photos = photos;
        this.links = links;
        this.privacy = privacy;
        this.clock = clock;
    }

    /** Avaliação com as fotos, cada uma com a URL temporária de leitura. */
    public record AssessmentWithPhotos(Assessment assessment, List<ManagePhotos.Viewable> photos) {}

    /** Vínculo com o nome do profissional e o consentimento dado no aceite. */
    public record LinkWithConsent(Link link, String professionalName, Optional<PrivacyRecords.Consent> consent) {}

    public record Export(
            Instant exportedAt,
            Optional<ManageClientProfile.Result> profile,
            Optional<HealthProfile> health,
            List<HealthConsents.Accepted> healthConsents,
            List<AssessmentWithPhotos> assessments,
            List<LinkWithConsent> links,
            List<PrivacyRecords.HealthRead> healthReads,
            List<PrivacyRecords.AccessRecord> accessLog) {}

    public Export mine(String clientAccount) {
        UUID clientId = UUID.fromString(clientAccount);
        Map<UUID, PrivacyRecords.Consent> consentByLink = privacy.consentsOf(clientId).stream()
                .collect(
                        Collectors.toMap(PrivacyRecords.Consent::linkId, Function.identity(), (newer, older) -> newer));
        return new Export(
                clock.instant(),
                profiles.findMine(clientAccount),
                health.findMine(clientAccount),
                health.consentsOf(clientAccount),
                assessments.listMine(clientAccount).stream()
                        .map(assessment ->
                                new AssessmentWithPhotos(assessment, photos.listMine(clientAccount, assessment.id())))
                        .toList(),
                links.listMine(clientAccount).stream()
                        .map(found -> new LinkWithConsent(
                                found.link(),
                                found.professionalName(),
                                Optional.ofNullable(
                                        consentByLink.get(found.link().id()))))
                        .toList(),
                privacy.healthReadsOf(clientId),
                privacy.accessLogOf(clientId));
    }
}
