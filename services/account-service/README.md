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
- Um plano no MVP (`PROFESSIONAL_MONTHLY`). O período pago só muda pelo pagamento (DEV-0100), que estende e nunca
  encurta.

## Pagamento pelo Asaas (DEV-0100)

[ADR-015](../../docs/00-oficial/adr/ADR-015-pagamento-asaas.md). Só cartão de crédito, em cobrança recorrente mensal,
pelo checkout hospedado do Asaas: o pagador informa os dados, inclusive o CPF/CNPJ, na página do Asaas, e o FitHub não
guarda nenhum deles.

| Rota | Quem | Resultado |
|---|---|---|
| `POST /professionals/me/subscription/checkout` | profissional | 201 com o link do checkout, o valor e se é o preço de lançamento; 404 sem cadastro; 409 com período pago valendo ou com assinatura viva no Asaas. O checkout aberto de até 23 h é reaproveitado |
| `POST /webhooks/asaas` | o Asaas | 200; 401 sem o token certo no header `asaas-access-token`; 400 sem o tipo do evento. Fora do JWT e do OpenAPI |

- **Primeira cobrança:** vence no fim do teste grátis, ou hoje se ele já acabou.
- **Valor:** o preço de lançamento (`fithub.subscription.price.launch`, padrão R$ 150) vale para quem assina até
  `launch-until` (padrão provisório 30/06/2027, o fim do beta; dia de São Paulo). Depois da data, vale o preço cheio
  (`monthly`, R$ 200).
- **Fim do lançamento:** no `launch-months`º pagamento confirmado (padrão 3º), o valor da assinatura passa ao cheio no
  Asaas, inclusive na cobrança já gerada.
- **Webhook:**
  - `PAYMENT_CONFIRMED` e `PAYMENT_RECEIVED` de uma cobrança da assinatura gravam o pagamento uma vez só, pelo id
    dela;
  - cada pagamento estende o `paid_until` até o vencimento seguinte, com 1 dia de folga, e grava o
    `AssinaturaAlterada`;
  - `PAYMENT_REFUNDED`, `PAYMENT_CHARGEBACK_REQUESTED` e `PAYMENT_CHARGEBACK_DISPUTE` de uma cobrança gravada
    marcam a cobrança como estornada uma vez só e encerram o `paid_until` no momento do evento, nunca depois do que já
    estava, com o `AssinaturaAlterada` (DEV-0182). A assinatura do Asaas continua viva: a próxima cobrança dela reabre o
    período, e o checkout novo só abre depois do fim dela. O estorno de uma cobrança não gravada é ignorado com aviso;
  - `SUBSCRIPTION_CREATED` liga a assinatura ao checkout; `SUBSCRIPTION_INACTIVATED` e `SUBSCRIPTION_DELETED` registram
    o fim e liberam um checkout novo;
  - evento sem os campos de que precisa é ignorado com aviso de ids (um erro faria o Asaas reentregar sem fim);
  - os demais eventos são aceitos e ignorados.
- **Ligação:** a assinatura do Asaas é ligada ao profissional pelo `checkoutSession` dela, consultado na criação da
  assinatura (ou no primeiro pagamento, se a criação não chegou). Uma assinatura viva por profissional: duas
  cobrariam o cartão todo mês.
- **Configuração:**
  - `fithub.payment.asaas.base-url` (padrão o sandbox);
  - `api-key` e `webhook-token`, só do ambiente (`ASAAS_API_KEY`, `ASAAS_WEBHOOK_TOKEN`);
  - `success-url` e `cancel-url`, a volta para a tela de assinatura da web.
- **Métricas:** `fithub.professionals.trial.ended` e `converted` (conversão do teste para pago), e
  `fithub.professionals.paid.last.month` e `retained` (retenção mensal), só agregadas por tipo (DEV-0113).

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
  da lista e apaga o marcador no fim. O usuário já ausente conta como apagado. Falha do Keycloak (ou do banco) mantém
  o marcador, e a rodada é tentada de novo em 1 minuto (`fithub.deleted-accounts.retry-interval`), não no ciclo de
  6 h; quando ela passa, o intervalo volta ao do expurgo (DEV-0175). Sem marcador, nada é reapagado.
- O id sai da lista depois de 30 dias, quando nenhum backup tem mais o usuário. O log traz só quantidades.
