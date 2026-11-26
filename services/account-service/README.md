# account-service

Dono de: profissional (tipo, CREF/CRN), organização e assinatura.

Esqueleto da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Cadastro do profissional (DEV-0029)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /professionals/me` | Qualquer conta autenticada | 201 cria; 200 se o mesmo cadastro já existia; 409 se a conta já tem cadastro diferente; 400 com o campo inválido |
| `GET /professionals/me` | Qualquer conta autenticada | 200 com o cadastro da própria conta; 404 se não houver |

- A conta é sempre o `sub` do token. Não existe leitura do cadastro de outra pessoa.
- O id do profissional é o próprio `sub` da conta (DEV-0149, [ADR-009](../../docs/00-oficial/adr/ADR-009-identidade-comum-entre-servicos.md)):
  os outros serviços reconhecem o profissional pelo token. Domínio e CHECK no banco (V4) garantem.
- Corpo do `POST`: `type` (`PERSONAL` ou `NUTRITIONIST`), `registrationNumber` (1 a 6 dígitos),
  `registrationState` (UF) e, só no CREF, `registrationCategory` (`G` ou `P`).
- O nome do profissional vem do perfil do Keycloak (claim `name`, ou nome e sobrenome); sem ele, 400 com o
  campo `name`.
- O cadastro grava o evento `ProfissionalCadastrado` (`account.professional-registered.v1`) no outbox, na
  mesma transação, só na primeira vez ([libs/event-contracts](../../libs/event-contracts/README.md)).
- Depois do cadastro, o serviço atribui o papel no Keycloak ([ADR-008](../../docs/00-oficial/adr/ADR-008-atribuicao-de-papel-pelo-account-service.md)).
  Enquanto `roleStatus` for `PENDING`, o token ainda não traz o papel: o front renova a sessão.
- Configuração: `fithub.keycloak-admin.*` (URL do Keycloak e client) e o secret em
  `FITHUB_ACCOUNT_SERVICE_SECRET`; no compose local o padrão já casa com o realm. A retentativa roda a
  cada `fithub.professional.role-retry.interval` (30 s) e desliga com
  `fithub.professional.role-retry.enabled=false`.

## Assinatura (DEV-0099)

| Rota | Quem | Resultado |
|---|---|---|
| `GET /professionals/me/subscription` | profissional | Plano, estado (`TRIAL`, `ACTIVE`, `EXPIRED`), fim do teste, período pago e `validUntil`; 404 sem cadastro |

- Todo profissional novo nasce em teste grátis, na mesma transação do cadastro. A duração vem de
  `fithub.subscription.trial-days` (padrão 14). Os cadastros anteriores à V5 ganharam 14 dias a partir da migration.
- O estado não é guardado: sai das datas na leitura. Período pago no futuro é `ACTIVE`; senão, teste no futuro é
  `TRIAL`; senão, `EXPIRED`. `validUntil` é a maior das duas datas.
- O cadastro grava o `AssinaturaAlterada` (`account.subscription-changed.v1`) no outbox, na mesma transação, com
  `active` e o `validUntil` do teste (DEV-0101). Com a data, training e nutrition bloqueiam sozinhos quando ela passa,
  sem rotina agendada aqui.
- Um plano no MVP (`PROFESSIONAL_MONTHLY`). O preço e o meio de pagamento esperam a decisão da DEV-0098; o período
  pago só muda pelo pagamento (DEV-0100), que estende e nunca encurta.

## Login de quem excluiu a conta (DEV-0161)

O account-service consome o `ContaExcluida` (`client.account-deleted.v1`, fila `account.client-events`, com
`.dlq`) e aplica o [ADR-012](../../docs/00-oficial/adr/ADR-012-retencao-e-exclusao.md):

- **Conta só de cliente:** o usuário é apagado no Keycloak (`DELETE /admin/realms/fithub/users/{id}`), com o mesmo
  client confidencial que atribui os papéis. Usuário que já não existe conta como apagado.
- **Conta que também é de profissional:** o login fica, porque o lado profissional continua. O papel `client` vem do
  papel padrão do realm e fica com ela.
- **Consumo idempotente** pela `java-commons`. Falha do Keycloak volta para a fila (3 tentativas) e, esgotadas, vai
  para a `.dlq`. Payload fora do contrato vai direto para a `.dlq`. Nada do payload vai para log.

## Lista de logins apagados (DEV-0173)

O banco do Keycloak volta do backup com o usuário de quem excluiu a conta depois do backup (ADR-012, item 4):

- O consumo do `ContaExcluida` que apaga o login grava o id na lista `deleted_account`, com a data do evento. A conta
  que também é de profissional não entra. Gravar de novo o mesmo id mantém uma linha, com a data mais recente.
- Depois de restaurar um backup, a `infra/backup/restore.sh` grava um marcador em `restore_pending`. O expurgo
  periódico (`fithub.deleted-accounts.purge-interval`, 6 h), que também roda na subida, reapaga no Keycloak cada login
  da lista e apaga o marcador no fim. O usuário já ausente conta como apagado. Falha do Keycloak mantém o marcador, e
  o próximo ciclo tenta de novo. Sem marcador, nada é reapagado.
- O id sai da lista depois de 30 dias, quando nenhum backup tem mais o usuário. O log traz só quantidades.
