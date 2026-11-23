package com.fithub.account.domain.professional;

/**
 * Eventos do profissional para os outros serviços (porta do domínio, ADR-003). Chamada dentro da
 * transação do cadastro: se o cadastro for desfeito, o evento também é.
 */
public interface ProfessionalEvents {

    /** O cadastro foi concluído agora (nunca chamado para um pedido repetido). */
    void registered(Professional professional);
}
