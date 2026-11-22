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
fim (mensagem venenosa).

### Ocorrências

- **Revisão:** https://github.com/TitoStauffer/fithub/pull/175#pullrequestreview-6010511272 (MEDIUM) — DEV-0014 (#6) · **PR:** #175
  - **Impacto:** as guardas existiam, mas nenhum teste as derrubava; uma regressão passaria despercebida.
  - **Correção:** `MessagingGuardsTest` cobre cada guarda sem banco nem broker.
  - **Prevenção:** mutações N5 a N9 no PR, uma por guarda.

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
