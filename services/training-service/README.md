# training-service

Dono de: exercícios, restrições de mobilidade, fichas, execuções por série e recordes.

Esqueleto da DEV-0141, com o template da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Projeção de vínculos e clientes (DEV-0039)

- Fila `training.client-events`, durável e ligada a `fithub.client` para `client.link-activated.v1`,
  `client.link-ended.v1` e `client.client-updated.v1`. Consumo idempotente pela `java-commons`; 3 tentativas e
  depois `training.client-events.dlq`. Payload fora do contrato (JSON inválido ou JSON válido com campo faltando, tipo desconhecido ou nome em branco)
  é rejeitado sem voltar para a fila e termina na `.dlq`, sem payload no log e sem contar como processado.
- Só vínculos de personal entram na projeção (BR-03). Encerramento sempre vence: ativação atrasada ou repetida não
  reativa um vínculo encerrado. `ClienteAtualizado` atrasado não desfaz o mais recente.
- **Verificação de acesso por vínculo:** `ClientAccess.requireActiveLink(conta, clientId)`. Todo endpoint com
  `clientId` passa por ela; sem vínculo ativo, 404 (não revela que o cliente existe). Conta com `sub` fora do formato
  UUID também recebe 404.
- `GET /clients/{clientId}` (papel `personal`): dados básicos do cliente vinculado (nome, nascimento, gênero).

## Exercícios (DEV-0053)

- Exercício: nome (2 a 120 caracteres), um ou mais grupos musculares, equipamento, instruções (até 4000) e mídia por
  URL `https` de até 500 caracteres. O FitHub não hospeda vídeo nem imagem.
- Dois tipos: `CATALOG`, comum a todos os personais, e `OWN`, visível só para o dono (BR-13). O dono é o `sub` do
  token (ADR-009).
- Exercício importado guarda fonte, id externo, licença e autor ([ADR-010](../../docs/00-oficial/adr/ADR-010-base-de-exercicios.md));
  a API devolve a atribuição para a tela mostrar o crédito.
- `GET /exercises/{id}` (papel `personal` ou `client`): exercício do catálogo ou próprio. O de outro personal e o id desconhecido
  respondem o mesmo 404.
- `GET /exercises?name=&muscleGroup=&equipment=&page=&size=` (papel `personal` ou `client`, DEV-0055): o catálogo mais os
  exercícios próprios de quem busca, nunca os de outro personal (BR-13). O nome busca por trecho, sem diferenciar
  maiúsculas nem acentos (`fold_search` da migration V5, aplicada ao nome gravado e ao texto buscado; `%` e `_` são
  literais). Os filtros se combinam (E lógico). Resposta `{items, total, page, size}`, ordenada por nome; página a
  partir de 0, 20 itens por padrão e até 50. Filtro fora da lista, página negativa ou tamanho fora de 1 a 50 respondem
  400 com o campo em `errors`.
- **Exercício próprio** (DEV-0056, FR-24), papel `personal`:
  - `POST /exercises` cria (201, endereço em `Location`), com as regras do modelo; o dono vem sempre do token.
  - `PUT /exercises/{id}` edita e `DELETE /exercises/{id}` arquiva (204). Só o dono altera: exercício de outro
    personal, do catálogo ou inexistente responde o mesmo 404.
  - Arquivar não apaga (`archived_at`, migration V6): o exercício continua legível por id, com `archivedAt`, para as
    fichas antigas, mas sai da busca e não muda mais (editar responde 409). Arquivar de novo não troca a data. Só
    exercício próprio pode ser arquivado (domínio e `ck_exercise_archived_own`).

## Importação da base do wger (DEV-0054)

- Copia a API pública do wger (`/exerciseinfo/`, paginada por `limit`/`offset`) para o catálogo local, com o nome e
  as instruções em português quando houver tradução e em inglês quando não. Os ids de músculo, categoria e
  equipamento do wger viram os grupos e equipamentos do FitHub pelo mapa versionado em `WgerMapping`.
- **Desligada por padrão.** Para importar, suba o serviço com a variável abaixo; o job roda uma vez, numa thread à
  parte, depois que o serviço já está no ar:

  ```text
  FITHUB_EXERCISES_IMPORT_ENABLED=true
  ```

  `FITHUB_EXERCISES_IMPORT_BASE_URL` troca a raiz da API (padrão `https://wger.de/api/v2`). Não há endpoint público.
- **Pode rodar de novo:** atualiza pelo id externo (fonte + id), não duplica, não regrava o que não mudou e não apaga
  exercício que saiu da base.
- **Licença (ADR-010):** o texto guarda a licença e o autor da tradução usada (ou os do exercício, quando a tradução
  não tem). A mídia entra só com licença Creative Commons e autor registrados nela, e o crédito dela vai em
  `mediaLicense` e `mediaAuthor` da atribuição (migration V4). Sem mídia assim, o exercício entra sem mídia.
- Item sem nome, sem grupo muscular mapeável ou sem licença e autor do texto é pulado e só contado no log.
- **Falha da base** (erro HTTP, tempo esgotado, resposta fora do formato) interrompe o job; o que já foi importado
  fica como estava e o serviço continua no ar. O log traz só as contagens ou o tipo do erro.

## Restrições de mobilidade (DEV-0058)

- Restrição do aluno: região do corpo (`CERVICAL`, `SHOULDER`, `ELBOW`, `WRIST_HAND`, `THORACIC`, `LUMBAR`, `HIP`,
  `KNEE`, `ANKLE_FOOT`) e descrição de 1 a 500 caracteres (FR-26). É do aluno (BR-01): quando o vínculo acaba, o
  personal perde o acesso, mas ela continua.
- **Personal** (papel `personal`, sempre com vínculo ativo pelo `ClientAccess`): `GET` e `POST
  /clients/{clientId}/restrictions`, `PUT` e `DELETE /clients/{clientId}/restrictions/{id}`. Sem vínculo, ou com a
  restrição de outro aluno, 404. Região fora da lista ou descrição fora do tamanho respondem 400 com o campo.
- **Aluno** (papel `client`): `GET /me/restrictions`, só leitura. A nutricionista nunca vê (BR-03).
- Ao incluir na ficha um exercício de região restrita, a ficha traz o alerta (DEV-0062).

## Ficha de treino (DEV-0059)

- Ficha do personal para o aluno: nome (2 a 80), início, validade de 1 a 52 semanas (vence em início + validade) e de
  1 a 7 divisões em sequência, com a letra da posição (A a G) e nome opcional de até 60 caracteres (FR-28, FR-32).
- Estados: `DRAFT` → `PUBLISHED` → `REPLACED`. Publicar uma ficha deixa a que estava ativa para o mesmo aluno como
  `REPLACED`: uma ativa por vez (FR-34, índice único parcial `ux_workout_plan_published`).
- O conteúdo fica numa versão (`workout_plan_version` → `workout_division`) desde o rascunho.
- Rotas do personal (papel `personal`): `POST` e `GET /clients/{clientId}/plans`, `GET` e `PUT /plans/{planId}`, `POST /plans/{planId}/publish` e `GET /plans/expiring?withinDays=`
  (padrão 7, de 0 a 60; fichas publicadas de alunos com vínculo ativo que vencem no período ou já venceram, com o
  "hoje" de `America/Sao_Paulo`).
- Criar, listar, editar e publicar exigem vínculo ativo (404 sem ele). O autor continua lendo as fichas que criou depois
  que o vínculo acaba (BR-05); outro personal recebe 404.

## Prescrição por exercício (DEV-0060)

- Cada divisão tem até 30 exercícios, na ordem, com séries (1 a 20), repetições fixas ou em faixa (`repsMin` e
  `repsMax`, de 1 a 100; sem `repsMax`, fixas), carga sugerida (0 a 1000 kg, passos de 0,5), descanso (0 a 600 s),
  observação (até 500) e técnica opcional (`BI_SET`, `TRI_SET`, `DROP_SET`, `REST_PAUSE`), mais até 3 alternativas,
  diferentes do principal e sem repetir (FR-30, FR-31; migration V9).
- O `PUT /plans/{planId}` leva as divisões com os itens e troca o conteúdo do rascunho inteiro. O erro de um campo
  aponta a divisão e o item, como `divisions[1].items[0].sets`.
- Exercício (principal ou alternativa) precisa ser do catálogo ou próprio do autor e não estar arquivado; um que foi
  arquivado depois de entrar na ficha continua nela.
- Publicar exige ao menos um exercício em cada divisão (BR-10): senão, 400 com a divisão.

## Versões da ficha (DEV-0061)

- Editar a ficha publicada (`PUT /plans/{planId}` com `baseVersion`) cria a versão seguinte, já publicada; a anterior
  nunca muda (FR-33, BR-09). O rascunho continua editado no lugar, sem gerar versão.
- `baseVersion` é a versão em que a edição se baseou: obrigatória na publicada (400 sem ela) e, se não for a atual,
  409 sem gravar nada (outra edição chegou antes). Duas edições da mesma base ao mesmo tempo também dão 409, pela
  unicidade (ficha, número). Ficha substituída não aceita edição (409).
- Leitura: `GET /plans/{planId}/versions` e `/versions/{number}` para o autor (mesmo sem vínculo, BR-05);
  `GET /me/plans/{planId}/versions` e `/versions/{number}` para o aluno dono da ficha, só as versões publicadas (o
  rascunho responde 404). Os registros de execução do E6 vão apontar para a versão.

## Alerta de restrição na ficha (DEV-0062)

- A resposta da ficha (criar, ler, listar, editar, publicar) traz `alerts[]`: cada exercício, principal ou alternativa,
  que envolve uma região restrita do aluno, com divisão, posição do item, região e a descrição da restrição (FR-27).
- **Alerta, não bloqueio** (BR-11): salvar e publicar nunca são recusados por restrição.
- Calculado na hora, com as restrições atuais, e só com vínculo ativo: sem vínculo, o autor lê a ficha com `alerts`
  vazio, sem ver as restrições do aluno (BR-05). As versões antigas não trazem alerta.
- Mapa grupo muscular → regiões (`MuscleRegions`):

  | Grupo muscular | Regiões |
  |---|---|
  | `QUADRICEPS`, `HAMSTRINGS`, `GLUTES`, `ADDUCTORS` | `KNEE`, `HIP` |
  | `CALVES` | `ANKLE_FOOT`, `KNEE` |
  | `CHEST`, `SHOULDERS`, `TRICEPS`, `BICEPS` | `SHOULDER`, `ELBOW` |
  | `FOREARMS` | `WRIST_HAND`, `ELBOW` |
  | `BACK` | `SHOULDER`, `THORACIC` |
  | `LOWER_BACK`, `ABS` | `LUMBAR` |
  | `NECK` | `CERVICAL` |
  | `FULL_BODY` | todas |

## Treino do aluno: fichas ativas e sessão (DEV-0065)

- `GET /me/plans/active` (papel `client`): as fichas publicadas do aluno, cada uma com a versão atual, o próximo treino
  da sequência (`nextDivision`, `nextDivisionLetter`), a marca de vencida (o aluno continua vendo e registrando) e a
  sessão aberta, se houver. Os itens trazem nome, mídia e atribuição do exercício e das alternativas, inclusive o
  exercício próprio do personal (BR-13): o aluno não usa as rotas de exercício do personal.
- **Sessão** (`workout_session`, migration V10): `PUT /me/sessions/{sessionId}` com `planId` inicia o próximo treino; o id
  é gerado pelo app, então repetir não duplica. Uma sessão aberta por ficha (outra responde 409; índice único
  `ux_workout_session_open`). `POST /me/sessions/{sessionId}/finish` finaliza; finalizar de novo não muda nada.
- **Próximo treino:** a divisão seguinte à da última sessão finalizada, em ordem (A→B→C→A); sem sessão, A. Se a versão
  nova tiver menos divisões, volta ao começo pelo resto. A sessão guarda a versão da ficha (BR-09).
- Ficha de outro aluno, rascunho ou sessão de outro aluno respondem 404.
- Testes: o pool de conexões fica em 3 nos testes (`src/test/resources/config/application.yml`), porque cada
  configuração de teste sobe um contexto do Spring (aprendizado `testes-de-integracao.md`).

## Registro por série (DEV-0066)

- `PUT /me/sessions/{sessionId}/sets/{setId}` (papel `client`): carga (0 a 1000 kg, passos de 0,25) e repetições (0 a
  300) reais de uma série de um item da divisão (FR-48; migration V11). O id é gerado pelo app: repetir corrige a
  mesma série (só carga e repetições: outro item ou número com o mesmo id responde 409); outra série no mesmo lugar
  (item e número) responde 409. O id de uma série de outro treino não é corrigido (409) nem apagado (404). `DELETE` apaga.
- A série fica na sessão, que guarda a versão da ficha: depois de o personal editar a ficha, os registros feitos ficam
  na versão anterior e o próximo treino usa a nova (BR-09).
- **Correção no mesmo dia** (BR-08, QUESTÃO-04): até o fim do dia em que o treino começou, em `America/Sao_Paulo`; depois,
  409. Vale também para o treino já finalizado.
- `GET /me/sessions/{sessionId}`: o treino na versão da sessão, com o prescrito de cada item, as séries já feitas e as
  do último treino finalizado com o exercício (`last`), para o app pré-preencher.

## Alternativa e comentário do treino (DEV-0067)

- A série pode trazer `exerciseId`: vazio, vale o exercício do item; senão, precisa ser uma alternativa prevista no
  item (FR-49, "exercício indisponível"). Outro exercício responde 400 no campo.
- O pré-preenchimento usa o exercício realmente feito: o item traz `last` (do exercício do treino) e
  `alternativesLast` (do último treino com cada alternativa).
- `PUT /me/sessions/{sessionId}/feedback`: comentário até 500 caracteres e sensação de 1 (muito fácil) a 5
  (exaustivo), opcionais, no mesmo prazo da correção das séries (migration V12). O treino devolve os dois.

## Recordes pessoais (DEV-0068)

- Calculados na leitura, das séries registradas e dos recordes manuais; não há tabela derivada para manter, e apagar a
  série ou o lançamento desfaz o recorde.
- Por exercício: **maior carga** (com ao menos 1 repetição) e **1RM estimado** por Epley (`carga × (1 + reps/30)`),
  só de 1 a 12 repetições; com 1 repetição vale a própria carga. No empate, vale a marca mais antiga. Cada recorde traz
  a marca que o deu (carga, repetições, data, origem `SET` ou `MANUAL` e o id).
- `GET /me/records`, `POST /me/records` (201) e `DELETE /me/records/{recordId}` (204, 404 se for de outro aluno):
  o aluno lê e lança à mão (carga de 0,25 a 1000 kg em passos de 0,25, 1 a 300 repetições, data não futura, exercício
  do catálogo ou de uma ficha que recebeu; migration V13).
- `GET /clients/{clientId}/records`: o personal com vínculo ativo lê os recordes do aluno (404 sem vínculo). A
  nutricionista não tem rota (BR-03).

## Ficha própria do aluno (DEV-0069)

- Sem personal, ou junto com ele, o aluno monta a ficha na mesma estrutura (FR-52): rascunho, publicação, versões ao
  editar a publicada e exercício em cada divisão para publicar. Exercício só do catálogo (o próprio é do personal).
- `POST`/`GET /me/own-plans`, `GET`/`PUT /me/own-plans/{planId}` e `POST /me/own-plans/{planId}/publish` (papel
  `client`). Uma própria ativa por vez; publicar outra substitui a anterior.
- **QUESTÃO-03: coexiste.** A ficha própria convive com a do personal e é só do aluno: o personal não a vê (404 por id,
  ausente da lista). Não tem alerta de restrição (o alerta é para o personal, FR-27).
- Publicada, aparece em `GET /me/plans/active` e aceita sessão e séries como a do personal.
- Migration V14: `personal_id` opcional e `author` (`PERSONAL` ou `CLIENT`), com a CHECK `ck_workout_plan_author`
  ligando os dois, e o índice único `ux_workout_plan_own_published` (uma própria publicada por aluno).
- O aluno também lê e busca o catálogo em `/exercises` (papel `client`); exercício próprio de personal não aparece.

## Histórico de treinos (DEV-0072)

- `GET /me/sessions?page=` (papel `client`): os treinos finalizados do aluno, do mais novo para o mais antigo, em
  páginas de 20 (a partir de 0; negativa responde 400). Cada um traz a ficha e o treino na versão em que foi feito, as
  datas, quantas séries teve e a sensação. O detalhe é `GET /me/sessions/{sessionId}` (DEV-0066).
- O histórico e as fichas recebidas são do aluno: continuam depois que o vínculo acaba (BR-06).

## Evolução do treino (DEV-0091)

- `GET /clients/{clientId}/progress/training` (personal com vínculo ativo; sem vínculo, 404) e `GET /me/progress/training`
  (aluno), com `from` e `to` opcionais: padrão das últimas 4 semanas, até 26 semanas (FR-56, FR-58).
- **Aderência:** treinos finalizados contra esperados.
    - Cada ficha publicada espera, por semana, tantos treinos quanto as suas divisões (ABC = 3), na proporção dos dias em
      que valeu (da primeira publicação até a substituição).
    - O período é dividido em semanas de 7 dias a partir do início (a última pode ser menor).
    - O percentual sai do esperado exato, com uma casa; sem treino esperado, fica ausente.
- **Carga e volume por exercício:** um ponto por sessão finalizada com o exercício: a maior carga com ao menos 1
  repetição e o volume (soma de carga × repetições). A alternativa conta como o exercício que foi feito.
- O personal vê só as fichas dele: a ficha própria do aluno não entra (QUESTÃO-03). O aluno vê todas.
- `GET /professionals/me/clients/activity` (personal): cada aluno com vínculo ativo e a data do último treino finalizado
  nas fichas do personal, base da lista de inativos (FR-59).
- Tudo é calculado na leitura, sem tabela derivada. A nutricionista não tem rota (BR-03).


## Assinatura do profissional (DEV-0101)

- Consome o `AssinaturaAlterada` (`account.subscription-changed.v1`, fila `training.account-events`, com fila de
  mensagens mortas) e guarda em `professional_subscription` o `active` e o `validUntil` de cada profissional. O evento
  mais novo por `occurredAt` vence, inclusive fora de ordem.
- Sem assinatura válida (`validUntil` no passado ou, sem ele, `active` falso), criar, editar e publicar ficha
  respondem 403 ("Sua assinatura venceu. Renove para criar ou editar prescrições."). Ler o que já existe continua,
  para o profissional e para o cliente (FR-06, BR-14).
- Profissional de quem nenhum evento chegou não é bloqueado. Isso vale para quem se cadastrou antes desta entrega,
  porque o account-service só publica o evento no cadastro.

## Exclusão do cliente (DEV-0162)

O consumidor dos eventos do client-service também recebe o `ContaExcluida` (`client.account-deleted.v1`) e aplica o
[ADR-012](../../docs/00-oficial/adr/ADR-012-retencao-e-exclusao.md), na transação do consumo idempotente:

- **Sai de vez:** sessões e séries registradas (com os comentários), recordes manuais, restrições de mobilidade, a ficha própria e as projeções do cliente e dos vínculos.
- **Fica:** as fichas dos personais, com todas as versões, com o id do cliente trocado por um id aleatório (o mesmo para todas
  as prescrições dessa pessoa).
- **Lista de contas excluídas** (`deleted_account`): só o id e a data, com expurgo a cada
  `fithub.deleted-accounts.purge-interval` (padrão 6 h) do que passou de 30 dias.
- O `VinculoEncerrado` da exclusão chega antes do `ContaExcluida` (mesma fila, ordem do outbox) e corta o acesso
  mesmo que o consumo do segundo atrase.
