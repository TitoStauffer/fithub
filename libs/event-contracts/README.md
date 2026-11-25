# event-contracts

Contratos dos eventos entre serviços (DEV-0018, ADR-003): um JSON Schema (draft 2020-12) por evento e
versão, mais o utilitário que valida um payload contra ele. **Só contrato**: nenhum tipo Java de evento
mora aqui. Cada serviço modela o evento do seu jeito (AGENTS.md: domínio compartilhado é proibido), e o
teste confere que o JSON produzido ou lido cumpre o schema.

Os serviços usam a lib **com escopo `test`**:

```xml
<dependency>
  <groupId>com.fithub</groupId>
  <artifactId>event-contracts</artifactId>
  <scope>test</scope>
</dependency>
```

## Eventos

O nome técnico é a routing key, `<servico-de-origem>.<evento>.v<versao>`, em inglês como todo
identificador (AGENTS.md). A exchange é a `topic` do serviço de origem, `fithub.<servico>`.

| Evento (ENGENHARIA.md §3) | Routing key | Campos | Consumidores |
|---|---|---|---|
| VinculoAtivado | `client.link-activated.v1` | `linkId`, `clientId`, `professionalId`, `professionalType`, `consentScope`, `occurredAt` | training, nutrition (publicado pelo client-service desde a DEV-0038) |
| VinculoEncerrado | `client.link-ended.v1` | `linkId`, `clientId`, `professionalId`, `professionalType`, `endedBy`, `occurredAt` | training, nutrition (publicado pelo client-service desde a DEV-0038) |
| ClienteAtualizado | `client.client-updated.v1` | `clientId`, `name`, `birthDate` (ou `null`), `gender` (ou `null`), `occurredAt` | training, nutrition (publicado pelo client-service desde a DEV-0034) |
| ContaExcluida | `client.account-deleted.v1` | `clientId`, `occurredAt` | todos |
| ProfissionalCadastrado | `account.professional-registered.v1` | `professionalId`, `professionalType`, `name`, `occurredAt` | training, nutrition, client (publicado pelo account-service desde a DEV-0030; consumido pelo client-service desde a DEV-0035) |
| AssinaturaAlterada | `account.subscription-changed.v1` | `professionalId`, `active`, `occurredAt`, `validUntil` (opcional, DEV-0101) | training, nutrition, client |

Os schemas ficam em [src/main/resources/fithub/event-contracts](src/main/resources/fithub/event-contracts),
com descrição e exemplo de cada campo. Valores comuns:
- ids são UUID (v7, `format: uuid`);
- instantes são ISO-8601 em UTC (`format: date-time`);
- `professionalType` é `personal_trainer` ou `nutritionist`.

O id do evento não está no payload: é o `messageId` da mensagem, gerado pelo `OutboxWriter`
(java-commons) e usado pelo consumo idempotente.

**Dados mínimos:** evento leva só o que o consumidor precisa. Dado de saúde, número de registro
(CREF/CRN) e contato não saem do serviço dono. O outbox apaga o evento publicado depois da retenção
(java-commons).

## Uso nos testes

```java
// Produtor: o payload que o serviço passa a OutboxWriter.append(routingKey, payload)
EventContracts.assertValid(EventContract.LINK_ACTIVATED_V1, payload);   // record, Map, JsonNode ou JSON

// Consumidor: o handler precisa ler o exemplo do contrato
String json = EventContracts.example(EventContract.LINK_ENDED_V1);

// Pela routing key, como chega na mensagem
List<String> violacoes = EventContracts.validate(message.getMessageProperties().getReceivedRoutingKey(), body);
```

`assertValid` lança `ContractViolationException` com todas as violações. Os formatos (`uuid`,
`date-time`, `date`) são verificados, não só anotados.

## Versionamento

A versão faz parte da routing key e do nome do arquivo. O schema é **fechado**
(`additionalProperties: false`): o teste do produtor falha em campo que o contrato não prevê. Em
produção, o **consumidor ignora campos desconhecidos** (Jackson com `FAIL_ON_UNKNOWN_PROPERTIES`
desligado), para que uma mudança compatível não derrube quem ainda não atualizou.

**Mudança compatível: mesma versão, schema editado no lugar.**
- Campo novo opcional, que não entra em `required`.
- Restrição afrouxada: `maxLength` maior, ou um campo obrigatório que passa a aceitar `null` sem que nenhum consumidor dependa dele.
- Descrição e exemplos.

**Mudança incompatível: nova versão do evento.**
- Remover ou renomear campo.
- Mudar tipo ou formato.
- Tornar um campo obrigatório.
- Acrescentar valor a um `enum`, porque o consumidor que trata cada valor pode não saber o novo.
- Mudar o significado de um campo.

Passo a passo de uma versão nova (`v2`):
1. Crie o arquivo `<origem>.<evento>.v2.schema.json` e a constante `..._V2` em `EventContract`. A `v1` continua.
2. O produtor passa a publicar as duas versões, cada uma com a sua routing key, até o último consumidor migrar para a `v2`.
3. Cada consumidor migra a sua fila para a `v2`.
4. Só então o produtor para de publicar a `v1`, e o schema e a constante da `v1` saem num PR próprio.

O teste `everySchemaFileHasAContractAndEveryContractHasASchemaFile` impede schema sem constante e
constante sem schema.
