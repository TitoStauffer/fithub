package com.fithub.eventcontracts;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo dos contratos de evento (ENGENHARIA.md §3). A routing key é o nome técnico do evento:
 * {@code <servico-de-origem>.<evento>.v<versao>}. Cada uma tem o schema
 * {@code fithub/event-contracts/<routing-key>.schema.json} no classpath.
 *
 * <p>Mudança incompatível cria uma constante nova com a versão seguinte; a antiga fica até o último
 * consumidor migrar (regra completa no README da lib).
 */
public enum EventContract {

    LINK_ACTIVATED_V1("client.link-activated.v1", "VinculoAtivado"),
    LINK_ENDED_V1("client.link-ended.v1", "VinculoEncerrado"),
    CLIENT_UPDATED_V1("client.client-updated.v1", "ClienteAtualizado"),
    ACCOUNT_DELETED_V1("client.account-deleted.v1", "ContaExcluida"),
    PROFESSIONAL_REGISTERED_V1("account.professional-registered.v1", "ProfissionalCadastrado"),
    SUBSCRIPTION_CHANGED_V1("account.subscription-changed.v1", "AssinaturaAlterada");

    static final String SCHEMA_DIRECTORY = "fithub/event-contracts/";

    private final String routingKey;
    private final String engineeringName;

    EventContract(String routingKey, String engineeringName) {
        this.routingKey = routingKey;
        this.engineeringName = engineeringName;
    }

    /** Routing key usada no outbox e na fila, por exemplo {@code client.link-activated.v1}. */
    public String routingKey() {
        return routingKey;
    }

    /** Nome do evento na ENGENHARIA.md §3, por exemplo {@code VinculoAtivado}. */
    public String engineeringName() {
        return engineeringName;
    }

    /** Serviço de origem: o primeiro segmento da routing key ({@code client}, {@code account}...). */
    public String originService() {
        return routingKey.substring(0, routingKey.indexOf('.'));
    }

    /** Exchange topic do serviço de origem (ADR-003), por exemplo {@code fithub.client}. */
    public String exchange() {
        return "fithub." + originService();
    }

    String schemaResource() {
        return SCHEMA_DIRECTORY + routingKey + ".schema.json";
    }

    public static Optional<EventContract> forRoutingKey(String routingKey) {
        return Arrays.stream(values()).filter(contract -> contract.routingKey.equals(routingKey)).findFirst();
    }
}
