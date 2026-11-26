# Aprendizados — auditoria de acesso

Registro das leituras de dados sensíveis do cliente pelo profissional (NFR-03, ADR-012, item 6).

## toda-resposta-com-dado-sensivel-registra-a-leitura

**Regra:** a auditoria cobre toda resposta que mostra ao profissional um dado sensível do cliente, não só a rota
"dona" do dado. Isso inclui os derivados: alertas, resumos ou listas que trazem a descrição, a região ou o valor do
dado. Antes de declarar a auditoria completa, procure no OpenAPI os schemas que carregam o dado (ou parte dele) e
confira que cada rota que os devolve grava o registro. Na dúvida, registra a mais, nunca a menos.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #312, no SHA `a05341f` (MEDIUM) — DEV-0171 (#309) · **PR:** #312
  - **Impacto:** o alerta de restrição do plano alimentar (`MealItemAlert`, DEV-0084) mostrava a descrição da alergia
    ou intolerância à nutricionista sem gravar o registro. O alerta da ficha (DEV-0062) tinha a mesma lacuna no
    training (DEV-0170), com a região e a descrição da restrição de mobilidade. O `accessLog` da exportação ficava
    incompleto.
  - **Correção:** `MealPlanAlerts.of` e `PlanAlerts.of` registram a leitura quando carregam restrições do cliente para
    a resposta ao profissional.
  - **Prevenção:** esta regra, os testes `oPlanoComOAlertaDeRestricao…` e `aFichaComOsAlertasDeRestricao…` e os
    mutantes M7 e M8 de `mutations/DEV-0171.json`.
