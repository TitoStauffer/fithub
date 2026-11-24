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
- `GET /exercises/{id}` (papel `personal`): exercício do catálogo ou próprio. O de outro personal e o id desconhecido
  respondem o mesmo 404.
- `GET /exercises?name=&muscleGroup=&equipment=&page=&size=` (papel `personal`, DEV-0055): o catálogo mais os
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
- O alerta ao incluir na ficha um exercício de região restrita é a DEV-0062.

## Ficha de treino (DEV-0059)

- Ficha do personal para o aluno: nome (2 a 80), início, validade de 1 a 52 semanas (vence em início + validade) e de
  1 a 7 divisões em sequência, com a letra da posição (A a G) e nome opcional de até 60 caracteres (FR-28, FR-32).
- Estados: `DRAFT` → `PUBLISHED` → `REPLACED`. Publicar uma ficha deixa a que estava ativa para o mesmo aluno como
  `REPLACED`: uma ativa por vez (FR-34, índice único parcial `ux_workout_plan_published`).
- O conteúdo fica numa versão (`workout_plan_version` → `workout_division`) desde o rascunho; editar a ficha
  publicada criando outra versão é a DEV-0061.
- Rotas do personal (papel `personal`): `POST` e `GET /clients/{clientId}/plans`, `GET` e `PUT /plans/{planId}` (o
  `PUT` só no rascunho; publicada responde 409), `POST /plans/{planId}/publish` e `GET /plans/expiring?withinDays=`
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
