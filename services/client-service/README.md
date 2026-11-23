# client-service

Dono de: perfil e perfil de saúde do cliente, vínculos e consentimentos, avaliações corporais e fotos.

Esqueleto da DEV-0141, com o template da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Perfil do cliente (DEV-0032)

| Rota | Resposta |
|---|---|
| `PUT /clients/me/profile` | 201 cria; 200 atualiza; 400 com o campo inválido |
| `GET /clients/me/profile` | 200 com o perfil e a idade calculada; 404 se ainda não houver |

- A conta é sempre o `sub` do token; não existe leitura do perfil de outra pessoa (a do profissional
  vinculado chega na DEV-0040).
- Campos: nome (1 a 200), data de nascimento, gênero (`FEMALE`, `MALE`, `OTHER`, `PREFER_NOT_TO_SAY`),
  altura em cm (100 a 250) e objetivo (`WEIGHT_LOSS`, `HYPERTROPHY`, `CONDITIONING`, `HEALTH`, `OTHER`).
- **Idade mínima de 18 anos** enquanto a QUESTÃO-05 do PRD (menores de idade) estiver aberta:
  `ClientProfile.MINIMUM_AGE`.
- A idade é calculada na leitura, nunca gravada (FR-09). Criar e atualizar são um upsert: pedidos
  simultâneos da mesma conta terminam com um perfil, e só um deles responde 201.

## Perfil de saúde (DEV-0033)

| Rota | Resposta |
|---|---|
| `PUT /clients/me/health` | 200 grava (substitui o anterior); 400 com o campo inválido; 409 sem o perfil do cliente |
| `GET /clients/me/health` | 200 com as condições e o texto livre; 404 se ainda não houver |

- Dado sensível (LGPD, art. 11): nunca vai para log, mensagem de erro ou evento. O teste
  `HealthProfileApiTest.oDadoDeSaudeNuncaApareceNoLog` confere a saída do serviço.
- Condições como conjunto (`DIABETES`, `HYPERTENSION`, `PREGNANCY`, `RECENT_SURGERY`, `OTHER`): repetidas
  contam uma vez, lista vazia é nenhuma. Texto livre opcional de até 1000 caracteres.
- Exige o perfil do cliente já criado (409 com Problem Details); a chave estrangeira garante o mesmo no banco.

## Evento ClienteAtualizado (DEV-0034)

- Gravado no outbox (`client.client-updated.v1`, exchange `fithub.client`) na mesma transação do perfil:
  gravação desfeita não publica.
- Sai na criação do perfil e quando nome, nascimento ou gênero mudam; mudar só altura ou objetivo não publica.
  O payload leva o estado completo desses campos, `clientId` (a conta do Keycloak) e `occurredAt`.
- A gravação trava o perfil (`select ... for update`) para comparar o antes e o depois. Sem o antes, o evento
  sai: um a mais é inofensivo para o consumidor, que substitui a cópia local.
