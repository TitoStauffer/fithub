package com.fithub.nutrition.application;

import com.fithub.nutrition.domain.access.ClientDirectory;
import com.fithub.nutrition.domain.access.LinkProjection;
import com.fithub.nutrition.domain.account.ClientErasure;
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
    private final ClientErasure erasure;

    public ProjectClientEvents(LinkProjection links, ClientDirectory clients, ClientErasure erasure) {
        this.links = links;
        this.clients = clients;
        this.erasure = erasure;
    }

    public void linkActivated(UUID linkId, UUID clientId, UUID professionalId, String type, Instant occurredAt) {
        // Cliente que já excluiu a conta: o evento atrasado (reprocessado da .dlq, por exemplo) não volta a gravar o id
        // nem o nome dele (DEV-0165, ADR-012). O consumo marca o evento como processado do mesmo jeito.
        if (erasure.isDeleted(clientId)) {
            return;
        }
        if (LINK_TYPE.equals(type)) {
            links.activate(linkId, clientId, professionalId, occurredAt);
        }
    }

    public void linkEnded(UUID linkId, UUID clientId, UUID professionalId, String type, Instant occurredAt) {
        if (erasure.isDeleted(clientId)) {
            return;
        }
        if (LINK_TYPE.equals(type)) {
            links.end(linkId, clientId, professionalId, occurredAt);
            if (!links.hasAnyActiveLink(clientId)) {
                clients.remove(clientId);
            }
        }
    }

    public void clientUpdated(ClientDirectory.ProjectedClient client) {
        // Sem guarda própria de conta excluída: a cópia só é guardada com vínculo ativo (DEV-0153), e o vínculo de quem
        // excluiu a conta não volta a ser ativado (acima).
        if (links.hasAnyActiveLink(client.clientId())) {
            clients.update(client);
        }
    }
}
