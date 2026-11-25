# Documentos legais

**Status:** rascunho para revisão jurídica (DEV-0103)
**Última revisão:** 25/11/2026

Textos que a pessoa usuária lê e aceita. Cada documento tem uma versão. Mudar o texto exige uma versão nova, e a
versão anterior continua no histórico do git, como prova do que foi aceito.

| Documento | Versão | Quem aceita | Onde a versão fica registrada |
|---|---|---|---|
| [Termos de uso](termos-de-uso.md) | v1 | Todos, no cadastro | Ainda não registrada (lançamento, E13) |
| [Política de privacidade](politica-de-privacidade.md) | v1 | Todos, no cadastro | Ainda não registrada (lançamento, E13) |
| [Termo de consentimento do vínculo](termo-de-consentimento.md) | v1 | Cliente, ao aceitar cada convite | `link` do client-service, com data e escopo (DEV-0036, NFR-01) |

## Regras

- O texto do termo de consentimento é o mesmo do `ConsentTerms` do client-service. Os dois mudam juntos: o texto
  novo ganha a versão seguinte em `ConsentTerms.CURRENT_VERSION`, e o aceite com a versão antiga passa a ser recusado.
- Os termos de uso e a política de privacidade são publicados para a pessoa usuária no lançamento (E13): no site, nas
  lojas e no cadastro do Keycloak.

## O que falta antes do beta

Estes pontos dependem do Tito e não travam o desenvolvimento:

- **Revisão jurídica** dos três textos.
- **Dados da empresa:** razão social, CNPJ, endereço e contato do encarregado (DPO). Estão marcados como `[a definir]`.
- **Retenção** depois da exclusão da conta: decidida pelo Agent na ausência do Tito, para revisão
  ([ADR-012](../adr/ADR-012-retencao-e-exclusao.md), DEV-0107).
- **Base legal das restrições** e do dado de saúde registrado pelo próprio cliente (débito DEV-0160, #288).
- **Clientes menores de idade** (QUESTÃO-05). Enquanto ela estiver aberta, o produto exige 18 anos ou mais.
- **Meio de pagamento** (QUESTÃO-01, DEV-0098), que entra como operador na política de privacidade.
