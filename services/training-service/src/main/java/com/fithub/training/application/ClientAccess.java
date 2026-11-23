package com.fithub.training.application;

import com.fithub.training.domain.access.ClientDirectory;
import com.fithub.training.domain.access.ClientNotFoundException;
import com.fithub.training.domain.access.LinkProjection;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Verificação de acesso por vínculo (DEV-0039, BR-02, BR-05): todo endpoint com {@code clientId} passa por aqui
 * antes de ler ou gravar dado do cliente. Sem vínculo ativo, {@link ClientNotFoundException} (404).
 */
@Service
public class ClientAccess {

    private final LinkProjection links;
    private final ClientDirectory clients;

    public ClientAccess(LinkProjection links, ClientDirectory clients) {
        this.links = links;
        this.clients = clients;
    }

    /** Exige vínculo ativo entre o profissional (o {@code sub} do token, ADR-009) e o cliente. */
    public void requireActiveLink(String professionalAccount, UUID clientId) {
        if (!links.hasActiveLink(UUID.fromString(professionalAccount), clientId)) {
            throw new ClientNotFoundException();
        }
    }

    /** Dados básicos do cliente para o profissional vinculado. */
    public ClientDirectory.ProjectedClient viewClient(String professionalAccount, UUID clientId) {
        requireActiveLink(professionalAccount, clientId);
        // O vínculo pode chegar antes do primeiro ClienteAtualizado: até lá, o cliente ainda não aparece.
        return clients.find(clientId).orElseThrow(ClientNotFoundException::new);
    }
}
