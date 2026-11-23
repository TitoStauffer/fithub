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

## Convites (DEV-0035)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /invitations` | `personal` ou `nutritionist` | 201 com link, código e `emailSent`; 400 e-mail inválido; 403 sem o papel; 409 se o `ProfissionalCadastrado` ainda não chegou |
| `GET /invitations` | idem | Convites do profissional, com a situação (`PENDING`, `EXPIRED`, `ACCEPTED`, `CANCELLED`) |
| `DELETE /invitations/{id}` | idem | 204 (repetir também é 204); 404 se não for dele; 409 se já aceito |
| `POST /invitations/lookup` | qualquer conta | `{"token"}` ou `{"code"}`: nome e tipo do profissional e o que será compartilhado; 404 desconhecido; 410 vencido, usado ou cancelado |
| `GET` e `POST /professionals/me/invite-code` | `personal` ou `nutritionist` | Consulta ou troca o código pessoal (o antigo deixa de valer) |

- Token (256 bits, base64url) e código (8 caracteres sem 0/O e 1/I) saem de `SecureRandom` e só existem em
  claro na resposta da criação e no e-mail; o banco guarda o hash SHA-256. Nada disso vai para log, e a consulta
  usa `POST` para o segredo não aparecer em URL.
- Convite vale 7 dias e é de uso único (o aceite é a DEV-0036). O link é `fithub.invitations.link-base` +
  token (padrão `fithub://convite/`, aberto pelo app mobile).
- E-mail pelo SMTP (`spring.mail.*`; padrão o Mailpit do compose em `localhost:1025`). Falha de envio não
  desfaz o convite: `emailSent` volta `false` e o profissional compartilha o link. O health não depende do SMTP.
- Projeção de profissionais: consumidor do `ProfissionalCadastrado` na fila `client.professional-registered`
  (idempotente pela `java-commons`; 3 tentativas e depois `client.professional-registered.dlq`). O id é o
  `sub` do profissional (ADR-009), então o token identifica quem convida.

## Aceite e consentimento (DEV-0036)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /links` | cliente (perfil já criado) | `{"token"}` ou `{"code"}` (convite ou código pessoal), `"consent": true`, `"termsVersion": "v1"`: 201 com o vínculo; 400 sem consentimento; 404 desconhecido; 409 vínculo ativo do mesmo tipo, consigo mesmo, termo desatualizado ou sem perfil; 410 convite sem validade |

- O termo (`ConsentTerms`, versão `v1`) e o escopo vêm na consulta do convite (`termsVersion`, `termsText`,
  `sharedData`); o aceite repete a versão mostrada. Mudar o texto exige uma versão nova.
- Vínculo, consentimento (data, escopo e versão; sem IP nem aparelho) e a baixa do convite numa transação.
- Garantias no banco: `ux_client_link_active_type` (1 ativo de cada tipo por cliente, BR-07),
  `uq_client_link_invitation` (um convite, um vínculo) e `ck_client_link_not_self`.
- Recusar é não aceitar: sem `consent: true`, nada é gravado e o convite continua pendente.
