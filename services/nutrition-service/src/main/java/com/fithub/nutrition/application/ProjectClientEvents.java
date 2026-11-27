package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.ClientDirectory;
import com.fithub.nutrition.domain.access.LinkProjection;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Mantém as projeções a partir dos eventos do client-service (DEV-0151). A cópia dos dados básicos do cliente só existe
 * enquanto ele tem vínculo ativo neste serviço (DEV-0153, minimização): o client-service republica o
 * {@code ClienteAtualizado} logo depois do {@code VinculoAtivado}, e a cópia sai quando o último vínculo termina.
 *
 * <p>Premissa: um consumidor por fila (o padrão do listener) e uma instância do serviço. A conferência do vínculo e a
 * gravação são passos separados; com consumidores concorrentes na mesma fila, um {@code ClienteAtualizado} e um
 * {@code VinculoEncerrado} simultâneos poderiam deixar uma cópia órfã (Code Review do PR #345).
 */
@Service
public class ProjectClientEvents {

    /** Tipo de vínculo que este serviço projeta (BR-03): o nutrition libera o acesso só ao nutricionista. */
    public static final String LINK_TYPE = "nutritionist";

    private final LinkProjection links;
    private final ClientDirectory clients;

    public ProjectClientEvents(LinkProjection links, ClientDirectory clients) {
        this.links = links;
        this.clients = clients;
    }

    public void linkActivated(UUID linkId, UUID clientId, UUID professionalId, String type, Instant occurredAt) {
        if (LINK_TYPE.equals(type)) {
            links.activate(linkId, clientId, professionalId, occurredAt);
        }
    }

    public void linkEnded(UUID linkId, UUID clientId, UUID professionalId, String type, Instant occurredAt) {
        if (LINK_TYPE.equals(type)) {
            links.end(linkId, clientId, professionalId, occurredAt);
            if (!links.hasAnyActiveLink(clientId)) {
                clients.remove(clientId);
            }
        }
    }

    public void clientUpdated(ClientDirectory.ProjectedClient client) {
        if (links.hasAnyActiveLink(client.clientId())) {
            clients.update(client);
        }
    }
}
