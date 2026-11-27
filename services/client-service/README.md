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
| `PUT /clients/me/health` | 200 grava (substitui o anterior); 400 com o campo inválido, ou sem o aceite do termo do perfil de saúde (`consent`) enquanto a conta não aceitou a versão vigente; 409 sem o perfil do cliente, ou termo desatualizado (DEV-0179) |
| `GET /clients/me/health` | 200 com as condições e o texto livre; 404 se ainda não houver |
| `GET /clients/me/health/terms` | 200 com o termo do perfil de saúde (`termsVersion`, `termsText`) e se a conta já o aceitou (`accepted`). O aceite fica em `health_consent`, com a versão e o momento, sai com a conta na exclusão e aparece na exportação em `healthConsents` (DEV-0179) |

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
| `POST /links` | cliente (perfil já criado) | `{"token"}` ou `{"code"}` (convite ou código pessoal), `"consent": true`, `"termsVersion": "v2"`: 201 com o vínculo; 400 sem consentimento; 404 desconhecido; 409 vínculo ativo do mesmo tipo, consigo mesmo, termo desatualizado ou sem perfil; 410 convite sem validade |

- O termo (`ConsentTerms`, versão `v2` desde a DEV-0160, com as restrições) e o escopo vêm na consulta do convite (`termsVersion`, `termsText`,
  `sharedData`); o aceite repete a versão mostrada. Mudar o texto exige uma versão nova.
- Vínculo, consentimento (data, escopo e versão; sem IP nem aparelho) e a baixa do convite numa transação.
- Garantias no banco: `ux_client_link_active_type` (1 ativo de cada tipo por cliente, BR-07),
  `uq_client_link_invitation` (um convite, um vínculo) e `ck_client_link_not_self`.
- Recusar é não aceitar: sem `consent: true`, nada é gravado e o convite continua pendente.

## Encerramento e listas de vínculos (DEV-0037)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /links/{id}/end` | cliente ou profissional que é parte | 200 com `status`, `endedAt` e `endedBy` (repetir devolve o primeiro encerramento); 404 se a conta não for parte |
| `GET /links` | cliente | Vínculos ativos e encerrados, com o nome do profissional, a versão do último termo aceito (`consentVersion`) e `consentOutdated` (ativo com termo de versão anterior) |
| `POST /links/{id}/consent` | cliente do vínculo | `"consent": true`, `"termsVersion"` vigente: 200 com a versão que passou a valer; aceitar de novo a vigente não grava nada; 400 sem consentimento; 404 se a conta não for o cliente; 409 termo desatualizado ou vínculo encerrado (DEV-0160) |
| `GET /professionals/me/clients` | `personal` ou `nutritionist` | Clientes com vínculo ativo (só nome e data; perfil e saúde são da DEV-0040) |

- O encerramento é imediato e definitivo (BR-05): o vínculo encerrado não volta, e o cliente pode aceitar outro
  profissional do mesmo tipo (BR-07). `ck_client_link_ended` exige data e autor no encerrado.
- Dois encerramentos ao mesmo tempo: vale o que o banco gravou primeiro (`update ... where status = 'ACTIVE'`).

## Eventos de vínculo (DEV-0038)

- `VinculoAtivado` (`client.link-activated.v1`) no aceite e `VinculoEncerrado` (`client.link-ended.v1`) no
  encerramento, gravados no outbox na mesma transação (exchange `fithub.client`). Operação desfeita não publica.
- Os payloads levam só ids (os `sub` das contas, ADR-009), o tipo, o escopo do consentimento (`profile`,
  `health_profile`, `body_assessments`, `photos`), quem encerrou e as datas; nenhum dado do perfil ou da saúde.
- Encerrar de novo, ou perder a corrida de um encerramento simultâneo, não publica outro `VinculoEncerrado`.

## Leitura pelo profissional vinculado (DEV-0040)

| Rota | Quem | Resposta |
|---|---|---|
| `GET /professionals/me/clients/{clientId}/profile` | `personal` ou `nutritionist` com vínculo ativo | 200 com o perfil e a idade; 404 sem vínculo ativo |
| `GET /professionals/me/clients/{clientId}/health` | idem | 200 com a saúde; 404 sem vínculo ativo ou sem saúde informada |

- Vínculo encerrado corta o acesso na hora (BR-05). Personal e nutricionista vinculados leem os dois (BR-04).
- Cada leitura da saúde grava `health_access_log` (profissional, cliente, data) antes de o dado sair; a tabela não
  tem coluna para o conteúdo lido (NFR-03).

## Avaliação corporal (DEV-0044)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /clients/me/assessments` | cliente | 201, avaliação `ONLINE` com o cliente como autor; 400 com o campo |
| `GET /clients/me/assessments` | cliente | Histórico, da medição mais recente para a mais antiga |
| `DELETE /clients/me/assessments/{id}` | cliente | 204; 404 se não for dele |
| `POST /professionals/me/clients/{clientId}/assessments` | profissional com vínculo ativo | 201, avaliação `PRESENTIAL` com o profissional como autor; 404 sem vínculo |
| `GET /professionals/me/clients/{clientId}/assessments` | idem | Histórico do cliente |
| `GET /clients/me/assessments/series` | cliente | Série da evolução corporal (DEV-0093); `from` e `to` opcionais, 400 se invertidos |
| `GET /professionals/me/clients/{clientId}/assessments/series` | profissional com vínculo ativo | A mesma série do cliente; 404 sem vínculo |

- Peso de 20 a 400 kg; circunferências (pescoço, tórax, cintura, abdômen, quadril, braço, antebraço, coxa e
  panturrilha) de 10 a 300 cm, opcionais; até 1 casa decimal; medição não pode estar no futuro.
- `sexForFormulas` (`FEMALE` ou `MALE`) é opcional e só serve aos cálculos de % de gordura (DEV-0045 e 0046).
- Imutável: corrige-se registrando outra; só o cliente, dono dos dados, exclui.
- **Dobras e Pollock (DEV-0045):** só na presencial, em mm (1 a 80): peitoral, axilar média, tríceps, subescapular,
  abdominal, suprailíaca e coxa. Com `sexForFormulas` e o nascimento do perfil, a gravação calcula o % de gordura por
  Jackson & Pollock de 3 e 7 dobras (equação de Siri), devolvido em `bodyFat` com a origem (`POLLOCK_3`, `POLLOCK_7`).
  Sem os dados do protocolo, a avaliação é gravada sem o valor.
- **Marinha (DEV-0046):** na avaliação online, com `sexForFormulas`, pescoço, cintura, quadril (mulher) e a altura do
  perfil, a gravação estima o % de gordura pela fórmula da Marinha dos EUA (origem `NAVY`) e grava a altura usada.
  Medida faltando: avaliação gravada sem a estimativa (fluxo alternativo do PRD).
- **Série da evolução (DEV-0093, FR-56 a FR-58):** o peso, uma série por circunferência medida (na ordem do formulário)
  e uma série de % de gordura por método (`POLLOCK_3`, `POLLOCK_7`, `NAVY`, `BIOIMPEDANCE`), em ordem de data da
  medição. Métodos nunca se misturam numa série (BR-12); valor não medido não vira ponto. Sem `from` e `to`, todo o
  histórico.
- **Bioimpedância (DEV-0047):** `bioimpedanceBodyFatPercent` (3 a 70%) em qualquer avaliação, sem cálculo, devolvido com a
  origem `BIOIMPEDANCE`, separado dos calculados.

## Fotos de evolução (DEV-0048)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /clients/me/assessments/{id}/photos/{pose}/upload-url` | cliente | URL pré-assinada de `PUT` (5 min) e `uploadId`; 400 se não for JPEG/PNG |
| `POST /clients/me/assessments/{id}/photos/{pose}/confirm` | cliente | Confere tipo e tamanho (até 10 MB) no objeto; substitui a foto anterior da pose |
| `GET /clients/me/assessments/{id}/photos` | cliente | Fotos com URL pré-assinada de leitura (5 min) |
| `DELETE /clients/me/assessments/{id}/photos/{pose}` | cliente | 204; o arquivo sai do bucket |
| `GET /professionals/me/clients/{clientId}/assessments/{id}/photos` | profissional com vínculo ativo | Fotos com URL de leitura; 404 sem vínculo |

- Poses `FRONT`, `SIDE` e `BACK`, uma foto por pose. O bucket nunca é público; URLs e chaves de objeto não vão para log.
- Excluir a avaliação ou a foto de uma pose apaga do bucket tudo o que está sob o prefixo dela, inclusive envios que
  nunca foram confirmados. A confirmação grava a foto nova antes de apagar a anterior e os envios pendentes da pose.
- Expurgo: a cada `fithub.storage.purge-interval` (padrão 1 h), saem os envios não confirmados gravados há mais de
  `fithub.storage.pending-max-age` (padrão 1 h). O log traz só a quantidade.
- Configuração: `S3_ENDPOINT` (padrão `http://localhost:8333`, o SeaweedFS do compose), `S3_PUBLIC_ENDPOINT` (o endereço
  que o app alcança, usado nas URLs assinadas), `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` e `S3_PHOTOS_BUCKET`
  (padrão `fithub-photos`, criado no primeiro envio se não existir).

## Exclusão da conta (DEV-0105)

| Rota | Quem | Resposta |
|---|---|---|
| `DELETE /clients/me` | cliente | 204; repetir também responde 204 |

Aplica o [ADR-012](../../docs/00-oficial/adr/ADR-012-retencao-e-exclusao.md):

- **Sai de vez, em uma transação:** perfil, saúde, avaliações e fotos registradas, vínculos e consentimentos, e o
  e-mail dos convites que o cliente aceitou ou que estavam pendentes para o e-mail do token.
- **Na mesma transação:** o `ContaExcluida` (`client.account-deleted.v1`, só o id) vai para o outbox, e o id entra na
  lista `deleted_account` (V13). O evento sai sempre, mesmo sem nada aqui, porque training e nutrition podem ter
  dado do cliente.
- **Depois do commit:** saem do bucket os objetos sob `clients/<id>/`. Com o armazenamento fora do ar, o expurgo de
  envios não confirmados os apaga, porque nenhuma foto registrada aponta mais para eles.
- **Fica:** o registro das leituras de dados sensíveis pelos profissionais (saúde, avaliações e fotos; DEV-0040 e
  DEV-0106, por 6 meses) e o lado profissional da mesma conta.
- **Expurgo da lista:** a cada `fithub.deleted-accounts.purge-interval` (padrão 6 h), saem os ids excluídos há mais
  de 30 dias. O log traz só a quantidade.
- O login no Keycloak e os dados de training e nutrition saem nos consumidores do evento (DEV-0161, DEV-0162 e
  DEV-0163).

## Exportação dos dados (DEV-0104)

| Rota | Quem | Resposta |
|---|---|---|
| `GET /clients/me/export` | cliente | Todos os dados que o client-service tem do cliente do token |

- **O que entra:** perfil, perfil de saúde, avaliações com as fotos (URL pré-assinada de 5 minutos), vínculos ativos
  e encerrados com o consentimento de cada um (escopo, versão do termo e data) e as leituras do perfil de saúde pelos
  profissionais (quem e quando, sem o conteúdo).
- O app junta esta exportação com a do training (DEV-0166) e a do nutrition (DEV-0167), na tela da DEV-0168.

## Auditoria de acesso (DEV-0106)

- Cada leitura de dado sensível pelo profissional vinculado fica registrada em `health_access_log` (V7 e V14): quem
  leu, de quem, o quê (`resource`: `HEALTH`, `ASSESSMENTS` ou `PHOTOS`) e quando, nunca o conteúdo.
- Gravam: a saúde (DEV-0040), a lista de avaliações, a série corporal e as fotos de uma avaliação. A leitura do
  próprio cliente não grava.
- **Expurgo:** a cada `fithub.access-log.purge-interval` (padrão 6 h), sai o que passou de 6 meses (legítimo
  interesse e dever de segurança, LGPD art. 7º, IX, e art. 46; ADR-012, item 6). Fica mesmo depois da exclusão da conta, até o prazo.
- **Exportação:** `accessLog` traz todas as leituras; `healthReads` segue só com as da saúde, por compatibilidade.

## Reaplicar as exclusões depois de restaurar um backup (DEV-0172)

- Depois de restaurar um backup (DEV-0108), o dado de quem excluiu a conta depois do backup volta. A restauração grava
  um marcador em `restore_pending`, e o expurgo periódico da lista `deleted_account`, que também roda na subida do
  serviço, reaplica a exclusão de cada conta da lista e apaga o marcador. Só apaga, sem evento e sem mudar a data da
  lista.
- Sem marcador, a reaplicação não roda: o dado criado depois da exclusão, como o perfil de cliente recriado por quem
  também é profissional, não é apagado por ela. A exceção é restaurar um backup feito depois da recriação e ainda
  com o id na lista (até 30 dias depois da exclusão): a reaplicação apaga também o perfil recriado.
- O e-mail dos convites pendentes não é conhecido na reaplicação e fica no convite restaurado (débito DEV-0174, #317).
