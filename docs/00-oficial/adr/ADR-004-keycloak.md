# ADR-004 — Keycloak como provedor de identidade

**Status:** aceito · **Data:** 21/11/2026 · **Decisor:** Tito

## Contexto

O MVP precisa de login por e-mail/senha e Google, recuperação de senha, sessões revogáveis e papéis
(personal, nutricionista, cliente, admin), para web e mobile.

## Decisão

Keycloak (OIDC) cuida de credenciais, login social, sessões e papéis. Cada serviço é um resource
server que valida o JWT (Spring Security). Web e mobile usam Authorization Code + PKCE. O módulo
`identity` do legado é aposentado. Autorização em duas camadas: papel do token + vínculo ativo com o
cliente, verificado em projeção local.

## Alternativas consideradas

- **Serviço de identidade próprio** (evoluir o `identity` do legado): mais controle, mas
  implementar login social, revogação e recuperação de senha.
- **Gerenciado (Auth0/Cognito):** zero operação, mas custo por usuário ativo cresce com o app
  gratuito do cliente e cria dependência de fornecedor.

## Consequências

- Mais um componente para operar e fazer backup; realm versionado no repositório.
- Telas de login com tema do FitHub no Keycloak.
