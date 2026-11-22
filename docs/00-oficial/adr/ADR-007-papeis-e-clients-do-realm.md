# ADR-007 — Papéis e clients do realm `fithub`

**Status:** aceito · **Data:** 22/11/2026 · **Decisor:** Agent técnico (autonomia delegada pelo Tito)

## Contexto

O ADR-004 definiu o Keycloak como provedor de identidade, com papéis no token e web e mobile em
Authorization Code + PKCE. Ao versionar o realm (DEV-0024) foi preciso decidir quais papéis existem,
quem os atribui e como os clients são configurados. O PRD diz que uma conta é profissional ou
cliente e que a mesma pessoa pode ter as duas (FR-03, QUESTÃO-02 ainda aberta), e que o cliente
pode usar o app sem profissional.

## Decisão

- **Papéis de realm:** `client`, `personal`, `nutritionist` e `admin`. Os serviços os leem de
  `realm_access.roles` como `ROLE_CLIENT`, `ROLE_PERSONAL`, `ROLE_NUTRITIONIST` e `ROLE_ADMIN`.
- **Toda conta nova recebe só `client`.** Qualquer pessoa pode usar o app como cliente; ninguém se
  torna profissional ou admin ao se cadastrar.
- **Papel de profissional é atribuído pelo `account-service`** quando o cadastro do profissional é
  concluído (DEV-0029), com um client próprio de menor privilégio. O papel é acrescentado: a conta
  mantém `client`. Se a QUESTÃO-02 for respondida com "não", a mudança fica nessa atribuição, sem
  mexer nos papéis.
- **`admin` é atribuído manualmente** no console do Keycloak.
- **Clients públicos `fithub-web` e `fithub-mobile`:** só Authorization Code com PKCE S256
  obrigatório. Fluxo implícito, login por senha direta e service account ficam desligados. Redirect
  URIs e origens têm host fixo.
- **O arquivo do realm não tem usuário, credencial nem secret.** O que for segredo (Google, SMTP de
  produção, client do `account-service`) entra por variável de ambiente.

## Alternativas consideradas

- **Tipo de conta fora do token**, só no `account-service`, com os outros serviços consultando uma
  projeção: evita a atribuição de papel no Keycloak, mas toda autorização por papel passaria a
  depender de evento e de projeção local, e o ADR-004 já prevê papel no token.
- **Papel escolhido pelo usuário no cadastro do Keycloak:** dispensa o `account-service`, mas deixa
  qualquer pessoa se declarar profissional sem informar CREF ou CRN (FR-04).
- **Client confidencial para a web, com backend intermediário:** tira o token do navegador, mas exige
  mais um componente; fica como evolução se a análise de risco pedir.

## Consequências

- Depois do cadastro de profissional, a sessão precisa ser renovada para o token trazer o novo papel
  (DEV-0031).
- O `account-service` passa a ter uma credencial com poder de atribuir papel: precisa de privilégio
  mínimo e de auditoria (DEV-0029).
- URLs de produção dos clients ainda não existem no arquivo: entram com o ambiente de produção.
- O Keycloak só importa o arquivo quando o realm ainda não existe. Mudança no arquivo exige recriar
  o realm no ambiente local; em produção, a mudança será aplicada por migração do realm.
