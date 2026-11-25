# Aprendizados — mensageria (outbox, consumidores, eventos)

Problemas confirmados na mensageria entre serviços (ADR-003) e como não repeti-los. Formato no
[README](README.md).

## ordem-de-eventos-exige-publicador-unico

**Regra:** a garantia de ordem do outbox precisa valer com **várias instâncias** do serviço.
`FOR UPDATE SKIP LOCKED` sozinho divide o trabalho entre instâncias e quebra a ordem: a segunda
instância publica eventos posteriores enquanto a primeira ainda tenta um anterior. Serialize o
publicador por banco de serviço (lock consultivo do Postgres) e ordene por uma sequência
monotônica de gravação, nunca só por timestamp. Isso importa principalmente quando a ordem decide
acesso (`VinculoEncerrado` antes de `VinculoAtivado` deixaria o acesso aberto).

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/175#pullrequestreview-6010511272 (MEDIUM) — DEV-0014 (#6) · **PR:** #175
  - **Impacto:** o README da lib prometia ordem e várias instâncias ao mesmo tempo; com duas instâncias, eventos do mesmo serviço podiam sair fora de ordem.
  - **Correção:** `pg_try_advisory_xact_lock` no início de cada ciclo (quem não obtém o lock sai sem publicar) e coluna `sequence bigserial` como ordem de publicação.
  - **Prevenção:** teste `onlyOnePublisherRunsAtATimeAcrossInstances` (outra conexão segura o lock e nada é publicado) e `eventsArePublishedInTheOrderTheyWereWritten`, provados por mutação.

## guarda-de-entrada-de-mensagem-tem-teste

**Regra:** toda validação de entrada da mensageria (exchange, routing key, consumidor, id do evento,
`messageId` da mensagem) tem teste que falha sem ela. Mensagem malformada é rejeitada **sem
reentrega** (`AmqpRejectAndDontRequeueException`); se virar uma exceção comum, volta para a fila sem
fim (mensagem venenosa). O teste precisa **chegar à guarda**: payload que quebra antes, no
desserializador, não prova nada sobre as checagens que vêm depois. Use JSON válido que só a guarda
recusa (campo faltando, valor fora do enum, texto em branco) e declare o mutante que a remove.

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/175#pullrequestreview-6010511272 (MEDIUM) — DEV-0014 (#6) · **PR:** #175
  - **Impacto:** as guardas existiam, mas nenhum teste as derrubava; uma regressão passaria despercebida.
  - **Correção:** `MessagingGuardsTest` cobre cada guarda sem banco nem broker.
  - **Prevenção:** mutações N5 a N9 no PR, uma por guarda.
- **Revisão:** Code Review do PR #206 em 23/11/2026 (MEDIUM) — DEV-0039 (#31) · **PR:** #206
  - **Impacto:** o único teste de payload inválido usava um `clientId` que não era UUID e parava no Jackson; as
    guardas de `parseLink` e `parseClient` (tipo de profissional, nome em branco, campos nulos) ficaram sem
    teste. Sem a de tipo, um evento desconhecido seria marcado como processado e perdido em silêncio.
  - **Correção:** casos com JSON válido fora do contrato em `ClientEventsConsumerTest`, conferindo a `.dlq`, a
    projeção intacta e que a mensagem não conta como processada.
  - **Prevenção:** mutantes M7 e M8 em `mutations/DEV-0039.json`.
- **Revisão:** Code Review do PR #286, no SHA `9beb87e` (MEDIUM) — DEV-0101 (#93) · **PR:** #286
  - **Impacto:** as guardas do `AccountEventsListener` novo (routing key e `professionalId`, `active` e
    `occurredAt` nulos), no training e no nutrition, não tinham mutante. O teste de integração com payload sem `active`
    passava com ou sem a guarda: sem ela, o unboxing lança NPE dentro do `processOnce`, o listener tenta de novo e a
    mensagem termina na `.dlq` do mesmo jeito.
  - **Correção:** `AccountEventsListenerGuardsTest` nos dois serviços chama o listener sem banco nem broker e exige
    `AmqpRejectAndDontRequeueException` para cada caso; mutantes M18 a M25 de `mutations/DEV-0101.json`.
  - **Prevenção:** chegar à `.dlq` não prova a guarda, porque as retentativas também levam a mensagem até lá. Teste a
    guarda pelo tipo da exceção (sem reentrega), sem broker, e declare um mutante por condição.

## dado-de-evento-tem-retencao

**Regra:** tabelas técnicas que guardam payload de evento (outbox) ou rastro de consumo têm expurgo
com retenção configurável. Payload pode carregar dado pessoal (LGPD), e a tabela cresce sem fim. A
retenção do registro de consumo precisa cobrir o maior atraso de reentrega, senão a deduplicação
deixa de funcionar.

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/175#pullrequestreview-6010511272 (MEDIUM, não bloqueante) — DEV-0014 (#6) · **PR:** #175
  - **Impacto:** eventos publicados e registros de consumo ficariam guardados para sempre.
  - **Correção:** `OutboxPublisher.purgeExpired` a cada hora: publicados com mais de 7 dias e consumos com mais de 30 dias (configuráveis); pendentes nunca são expurgados.
  - **Prevenção:** teste `purgeRemovesOnlyRowsOlderThanTheRetention`, provado por mutação (N3, N4).
