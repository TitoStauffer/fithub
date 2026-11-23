package com.fithub.client.domain.profile;

import java.util.Optional;

/** Persistência dos perfis de cliente (porta do domínio). */
public interface ClientProfileRepository {

    Optional<ClientProfile> findByAccountId(String accountId);

    /**
     * Como {@link #findByAccountId}, mas trava o perfil até o fim da transação: duas gravações da mesma
     * conta comparam o antes e o depois uma de cada vez. Exige transação aberta.
     */
    Optional<ClientProfile> findByAccountIdForUpdate(String accountId);

    /**
     * Cria o perfil da conta ou atualiza o existente, numa operação só: pedidos simultâneos da mesma
     * conta terminam com um perfil, o do último pedido. Devolve o perfil gravado e se esta gravação o
     * criou (decidido pelo banco, não por uma leitura anterior que pode estar velha).
     */
    Saved save(ClientProfile profile);

    record Saved(ClientProfile profile, boolean created) {}
}
