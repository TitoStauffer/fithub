# ADR-008 — Atribuição do papel de profissional pelo account-service

**Status:** aceito · **Data:** 22/11/2026 · **Decisor:** Agent técnico (autonomia delegada pelo Tito, na ausência dele; para revisão)

## Contexto

O ADR-007 decidiu que conta nova recebe só `client` e que o papel de profissional é atribuído pelo
`account-service` quando o cadastro do profissional é concluído (DEV-0029). Faltava decidir como o
serviço fala com o Keycloak, com que privilégio, e o que acontece se o Keycloak falhar no meio do
cadastro.

Conferido no Keycloak 26.8.2 local:

- A API de administração exige que o token tenha a audiência dela, dada pelo client scope
  `kc-admin-api`. Sem ele, qualquer token de conta de serviço recebe 401.
- Para atribuir um papel de realm, a conta de serviço precisa de `manage-users` e `view-realm`
  (client `realm-management`). Com só `manage-users`, recebe 403.
- Com esses dois papéis, a conta de serviço consegue atribuir **qualquer** papel de realm, inclusive
  `admin`. Ela não consegue listar clients, alterar o realm nem se dar `realm-admin`.

## Decisão

- **Client confidencial `fithub-account-service`** no realm versionado, só com client credentials:
  sem login de pessoa, sem redirect URI, `fullScopeAllowed` desligado, client scope `kc-admin-api`
  e só `manage-users` e `view-realm` mapeados no token. O secret fica fora do repositório
  (`FITHUB_ACCOUNT_SERVICE_SECRET`); o arquivo do realm tem só o marcador.
- **A trava do papel está no código.** O adaptador do Keycloak só atribui `personal` ou
  `nutritionist`, conforme o tipo cadastrado, confere o nome do papel devolvido pelo Keycloak antes
  de atribuir e exige que o id da conta seja um UUID (o `sub`), para não montar caminho arbitrário.
- **Primeiro grava, depois atribui.** O cadastro e a organização são gravados numa transação com o
  papel `PENDING`. Depois do commit, o serviço atribui o papel e marca `ASSIGNED`. Se o Keycloak
  falhar, o cadastro continua válido com `PENDING`, e uma tarefa agendada tenta de novo a cada 30 s.
  Atribuir um papel que a conta já tem não muda nada, então repetir é seguro. Um pedido de cadastro
  repetido com o papel pendente também tenta de novo.

## Alternativas consideradas

- **Permissões finas do Keycloak** (admin permissions v2), para restringir o client a mapear só
  `personal` e `nutritionist` no próprio Keycloak. É o certo para fechar o risco de escalada, mas
  exige configurar a autorização do realm; fica como débito de segurança.
- **Atribuir antes de gravar:** se o banco falhar depois, a conta fica com papel de profissional sem
  cadastro, o que é pior do que um cadastro com papel pendente.
- **Papel fora do token** (o account-service como fonte e os outros serviços consultando uma
  projeção): descartada no ADR-007.

## Consequências

- Quem tiver o secret do `fithub-account-service` pode se dar `admin` pela API do Keycloak. O secret
  é tão sensível quanto uma credencial de administração e só pode existir no ambiente do serviço,
  até as permissões finas restringirem o client.
- O front precisa renovar a sessão depois do cadastro para o token trazer o papel (DEV-0031).
- Um Keycloak fora do ar não impede o cadastro; o papel chega quando ele voltar.
- O Keycloak precisa estar acessível pelo account-service (`fithub.keycloak-admin.base-url`).
