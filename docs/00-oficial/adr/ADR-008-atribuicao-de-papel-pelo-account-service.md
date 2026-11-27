# ADR-008 — Atribuição do papel de profissional pelo account-service

**Status:** aceito · **Data:** 22/11/2026, atualizado em 27/11/2026 (DEV-0147) · **Decisor:** Agent técnico (autonomia delegada pelo Tito, na ausência dele; para revisão)

## Contexto

O ADR-007 decidiu que conta nova recebe só `client` e que o papel de profissional é atribuído pelo
`account-service` quando o cadastro do profissional é concluído (DEV-0029). Faltava decidir como o
serviço fala com o Keycloak, com que privilégio, e o que acontece se o Keycloak falhar no meio do
cadastro.

Conferido no Keycloak 26.8.2 local:

- A API de administração exige que o token tenha a audiência dela, dada pelo client scope
  `kc-admin-api`. Sem ele, qualquer token de conta de serviço recebe 401.
- Com `manage-users` e `view-realm` (client `realm-management`), a conta de serviço consegue atribuir
  **qualquer** papel de realm, inclusive `admin`. Foi a configuração inicial, com a trava só no código.
- Com as permissões finas de administração (admin permissions v2), dá para limitar o que a conta de
  serviço faz nos usuários e quais papéis ela mapeia (DEV-0147).

## Decisão

- **Client confidencial `fithub-account-service`** no realm versionado, só com client credentials:
  sem login de pessoa, sem redirect URI, `fullScopeAllowed` desligado, client scope `kc-admin-api`
  e só `view-realm` mapeado no token, para ler o papel que atribui. O secret fica fora do repositório
  (`FITHUB_ACCOUNT_SERVICE_SECRET`); o arquivo do realm tem só o marcador.
- **A trava do papel está no Keycloak (DEV-0147).** O realm liga `adminPermissionsEnabled`, e o client
  `admin-permissions` guarda uma política (o client `fithub-account-service`, e só ele) e duas
  permissões:
  - **nos usuários:** `view`, `manage` (apagar o login de quem excluiu a conta, DEV-0161) e `map-roles`;
  - **nos papéis:** só `map-role`, e só em `personal` e `nutritionist`, citados pelo id fixo que o
    realm dá a esses dois papéis.

  Assim, mesmo com o secret, o client não atribui `admin` nem `client`, não lista os clients e não
  altera o realm. A checagem do Keycloak de pé (`infra/keycloak/check-live-realm.mjs`, job
  `Local Infrastructure`) cria uma conta só para isso e confere cada caso.
- **O código continua conferindo.** O adaptador do Keycloak só pede `personal` ou `nutritionist`,
  conforme o tipo cadastrado, confere o nome do papel devolvido pelo Keycloak antes de atribuir e exige
  que o id da conta seja um UUID (o `sub`), para não montar caminho arbitrário.
- **Primeiro grava, depois atribui.** O cadastro e a organização são gravados numa transação com o
  papel `PENDING`. Depois do commit, o serviço atribui o papel e marca `ASSIGNED`. Se o Keycloak
  falhar, o cadastro continua válido com `PENDING`, e uma tarefa agendada tenta de novo a cada 30 s.
  Atribuir um papel que a conta já tem não muda nada, então repetir é seguro. Um pedido de cadastro
  repetido com o papel pendente também tenta de novo.

## Alternativas consideradas

- **Trava só no código, com `manage-users`:** foi a primeira versão. Quem tivesse o secret podia se dar
  `admin` pela API do Keycloak. Substituída pelas permissões finas na DEV-0147.
- **Atribuir antes de gravar:** se o banco falhar depois, a conta fica com papel de profissional sem
  cadastro, o que é pior do que um cadastro com papel pendente.
- **Papel fora do token** (o account-service como fonte e os outros serviços consultando uma
  projeção): descartada no ADR-007.

## Consequências

- O secret do `fithub-account-service` continua sensível: com ele, dá para apagar contas e dar papel de
  profissional. Mas não dá mais para se dar `admin`, nem mexer em clients ou no realm.
- Os papéis `personal` e `nutritionist` têm id fixo no realm, porque a permissão os cita pelo id.
  Recriar esses papéis com outro id quebra a atribuição, e o teste do realm confere os ids.
- O front precisa renovar a sessão depois do cadastro para o token trazer o papel (DEV-0031).
- Um Keycloak fora do ar não impede o cadastro; o papel chega quando ele voltar.
- O Keycloak precisa estar acessível pelo account-service (`fithub.keycloak-admin.base-url`).
