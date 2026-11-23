package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.ClientDirectory;
import com.fithub.nutrition.domain.access.LinkProjection;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Mantém as projeções a partir dos eventos do client-service (DEV-0039). */
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
        }
    }

    public void clientUpdated(ClientDirectory.ProjectedClient client) {
        clients.update(client);
    }
}
