package com.fithub.account.domain.professional;

/**
 * Atribuição do papel de profissional no provedor de identidade (porta do domínio). A implementação
 * só pode atribuir o papel do tipo pedido, nunca outro (ADR-008).
 */
public interface ProfessionalRoles {

    /**
     * Garante que a conta tenha o papel do tipo de profissional. Repetir não tem efeito. Lança exceção
     * se o provedor não confirmar: a atribuição fica pendente e é tentada de novo.
     */
    void assign(String accountId, Professional.Type type);
}
