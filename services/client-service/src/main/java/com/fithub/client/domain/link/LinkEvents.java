package com.fithub.client.domain.link;

import com.fithub.client.domain.invitation.SharedData;
import java.util.Set;

/**
 * Eventos do vínculo para training e nutrition (porta do domínio, DEV-0038). Chamados dentro da transação do
 * aceite ou do encerramento: se ela for desfeita, o evento também é.
 */
public interface LinkEvents {

    /** {@code VinculoAtivado}: o serviço do tipo do profissional libera o acesso (BR-03). */
    void activated(Link link, Set<SharedData> consentScope);

    /** {@code VinculoEncerrado}: o acesso é cortado (BR-05). */
    void ended(Link link);
}
