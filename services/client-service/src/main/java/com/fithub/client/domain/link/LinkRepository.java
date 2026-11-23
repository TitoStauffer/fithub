package com.fithub.client.domain.link;

import com.fithub.client.domain.invitation.SharedData;
import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Vínculos e consentimentos (porta do domínio). */
public interface LinkRepository {

    Optional<Link> findById(UUID id);

    /** Vínculo com o nome do profissional, para a lista do cliente. */
    record WithProfessional(Link link, String professionalName) {}

    /** Vínculo ativo com o nome do cliente, para a lista do profissional. */
    record ActiveClient(UUID linkId, UUID clientId, String clientName, Instant startedAt) {}

    /** Vínculos do cliente, ativos e encerrados, do mais novo para o mais antigo. */
    List<WithProfessional> listByClient(UUID clientId);

    /** Vínculos ativos do profissional, do mais novo para o mais antigo. */
    List<ActiveClient> listActiveByProfessional(UUID professionalId);

    /**
     * Grava o encerramento se o vínculo ainda estiver ativo, numa instrução só. Devolve {@code false} se outro
     * pedido já o encerrou (vale o primeiro).
     */
    boolean end(Link ended);

    /** Vínculo ativo do cliente com profissionais deste tipo, se houver. */
    Optional<Link> findActive(UUID clientId, ProfessionalType type);

    /**
     * Grava o vínculo e o consentimento (data, escopo e versão do termo, NFR-01). Chamado dentro da transação do
     * aceite. Lança {@link ActiveLinkConflict} se o banco recusar um segundo vínculo ativo do mesmo tipo (aceites
     * simultâneos).
     */
    void saveWithConsent(Link link, Set<SharedData> scope, String termsVersion, Instant grantedAt);

    /** O banco recusou o vínculo: já existe um ativo do mesmo tipo para o cliente (BR-07). */
    final class ActiveLinkConflict extends RuntimeException {

        public ActiveLinkConflict(Throwable cause) {
            super("Vínculo ativo do mesmo tipo já existe.", cause);
        }
    }
}
