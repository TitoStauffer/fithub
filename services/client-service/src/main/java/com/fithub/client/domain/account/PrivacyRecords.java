package com.fithub.client.domain.account;

import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.link.ProfessionalAccessAudit;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Registros de privacidade do cliente, para a exportação dos dados dele (porta do domínio, DEV-0104). */
public interface PrivacyRecords {

    /** O consentimento dado no aceite de um vínculo (DEV-0036). */
    record Consent(UUID linkId, Set<SharedData> scope, String termsVersion, Instant grantedAt) {}

    /** Uma leitura do perfil de saúde pelo profissional vinculado (DEV-0040), sem o conteúdo lido. */
    record HealthRead(UUID professionalId, String professionalName, Instant accessedAt) {}

    /** Uma leitura de dado sensível pelo profissional (DEV-0106): o quê e quando, sem o conteúdo. */
    record AccessRecord(
            UUID professionalId,
            String professionalName,
            ProfessionalAccessAudit.Resource resource,
            Instant accessedAt) {}

    /** Consentimentos do cliente, do mais novo para o mais antigo. */
    List<Consent> consentsOf(UUID clientId);

    /** Leituras do perfil de saúde do cliente, da mais nova para a mais antiga. */
    List<HealthRead> healthReadsOf(UUID clientId);

    /** Todas as leituras de dados sensíveis do cliente (saúde, avaliações e fotos), da mais nova para a mais antiga. */
    List<AccessRecord> accessLogOf(UUID clientId);
}
