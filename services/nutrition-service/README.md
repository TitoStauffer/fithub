# nutrition-service

Dono de: alimentos (TACO), restrições e preferências alimentares, planos e registro de refeições.

Esqueleto da DEV-0141, com o template da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Projeção de vínculos e clientes (DEV-0151)

- Fila `nutrition.client-events`, durável e ligada a `fithub.client` para `client.link-activated.v1`,
  `client.link-ended.v1` e `client.client-updated.v1`. Consumo idempotente pela `java-commons`; 3 tentativas e
  depois `nutrition.client-events.dlq`. Payload fora do contrato (JSON inválido ou JSON válido com campo faltando, tipo desconhecido ou nome em branco)
  é rejeitado sem voltar para a fila e termina na `.dlq`, sem payload no log e sem contar como processado.
- Só vínculos de nutricionista entram na projeção (BR-03). Encerramento sempre vence: ativação atrasada ou repetida não
  reativa um vínculo encerrado. `ClienteAtualizado` atrasado não desfaz o mais recente.
- **Verificação de acesso por vínculo:** `ClientAccess.requireActiveLink(conta, clientId)`. Todo endpoint com
  `clientId` passa por ela; sem vínculo ativo, 404 (não revela que o cliente existe). Conta com `sub` fora do formato
  UUID também recebe 404.
- `GET /clients/{clientId}` (papel `nutritionist`): dados básicos do cliente vinculado (nome, nascimento, gênero).

## Alimentos (DEV-0074)

- `food` (migration V3): da Tabela TACO (`TACO`, para todas as nutricionistas, com a fonte e o id externo para a
  importação ser reexecutável) ou próprio (`OWN`, com a dona; só ela vê, BR-13). Unidade base `G` ou `ML`.
- Valores por 100 da unidade base: energia de 0 a 900 kcal; proteína, carboidrato e gordura de 0 a 100 g, somando no
  máximo 100 g; fibra opcional (a TACO não traz para todo alimento). Etiqueta `vegan`. O domínio valida as mesmas
  regras das CHECKs, com o campo no erro.
- `GET /foods/{foodId}` (papel `nutritionist`): a TACO e os próprios da nutricionista; o próprio de outra responde
  404. Personal e cliente não têm rota de alimento.

## Importação da Tabela TACO (DEV-0075, [ADR-011](../../docs/00-oficial/adr/ADR-011-base-de-alimentos-taco.md))

- A TACO, 4ª edição (NEPA/UNICAMP, 2011), vem do CSV versionado `src/main/resources/taco/taco-4a-edicao.csv`
  (597 linhas da planilha oficial, gerado por [tools/taco](../../tools/taco/README.md)). O serviço não baixa nada.
- Os valores seguem a legenda da planilha: "Tr" (traço) e "NA" (não aplicável) valem 0; "*" (em reavaliação) e
  vazio ficam ausentes; carboidrato por diferença levemente negativo (erro analítico, 4 itens) vale 0. Item sem energia ou sem um macro (ou com descrição inválida) é pulado e listado no log:
  hoje são 6, e entram 591.
- A etiqueta vegano é curada no CSV, item a item (a TACO não traz); na dúvida, o item fica sem ela.
- Job desligado por padrão: `FITHUB_FOODS_IMPORT_ENABLED=true` importa uma vez na subida do serviço, em thread à
  parte. Reexecutável pelo par (fonte, número na TACO): atualiza, não duplica e não apaga.

## Medidas caseiras (DEV-0076)

- `food_measure` (migration V4): nome (2 a 40 caracteres) e quanto vale na unidade base do alimento (0,1 a 2000 g ou
  ml). Cada nutricionista cadastra as dela em qualquer alimento que vê (TACO ou próprio), e só ela vê (BR-13); a TACO
  não traz medidas caseiras. Até 10 por alimento; nome repetido no mesmo alimento (sem diferenciar maiúsculas) é
  barrado pelo índice único e responde 400.
- `POST /foods/{foodId}/measures` (201) e `DELETE /foods/{foodId}/measures/{measureId}` (204; a de outra, 404); o
  `GET /foods/{foodId}` traz as medidas da nutricionista.
- Conversão no domínio, para o plano (E8): medidas para gramas/ml (`FoodMeasure.toBaseUnit`) e os nutrientes da
  porção (`Food.nutrientsIn`, valor por 100 vezes a quantidade, a 1 casa).

## Alimentos e receitas próprios (DEV-0077)

- `POST /foods` (201), `PUT /foods/{foodId}` e `POST /foods/{foodId}/archive` (papel `nutritionist`): o alimento próprio
  com as regras do modelo. Só a dona lê, edita e arquiva; outra nutricionista e o da TACO respondem 404 (BR-13).
- Arquivar em vez de apagar (migration V5, `archived_at`): o arquivado sai da busca (DEV-0078), mas continua nos planos;
  a dona continua lendo. Arquivar de novo não muda a data.
- **Receita:** alimento próprio em gramas com ingredientes (1 a 30, alimento visível para a dona e que não seja
  receita, 0,1 a 5000 g ou ml) e o rendimento (1 a 20000 g), em `food_recipe_ingredient`. Os valores por 100 g são
  calculados ao salvar, pela soma de (valor por 100 × quantidade) dividida pelo rendimento, e gravados no alimento;
  editar um ingrediente depois não muda a receita até ela ser salva de novo. A fibra falta se algum ingrediente não a
  traz, e a receita é vegana só se todos os ingredientes forem. Ingrediente inválido responde 400 no campo
  `recipe.ingredients[i].foodId`.

## Busca de alimentos (DEV-0078)

- `GET /foods?name=&vegan=&page=&size=` (papel `nutritionist`): a TACO e os próprios da nutricionista, sem os
  arquivados e nunca os de outra (BR-13). O nome busca por trecho, sem diferenciar maiúsculas nem acentos
  (`fold_search` da migration V6, aplicada ao nome gravado e ao texto buscado; `%` e `_` são literais).
  `vegan=true` traz só os veganos e `vegan=false` só os não veganos. Página a partir de 0, 20 por padrão e até 50.
- Ordem pelo nome; no empate, o próprio antes da TACO. Parâmetro fora das regras responde 400 com o campo.

## Restrições alimentares (DEV-0080)

- `food_restriction` (migration V7): tipo (`ALLERGY`, `INTOLERANCE` ou `PREFERENCE`), descrição (2 a 200) e de 1 a 10
  termos (2 a 40 cada, sem repetir), que o alerta do plano (DEV-0084) procura no nome dos alimentos. Até 30 por paciente.
  Alergia e intolerância geram alerta; a preferência só aparece para a nutricionista.
- Nutricionista com vínculo ativo (BR-02): `GET`/`POST /clients/{clientId}/food-restrictions` e
  `PUT`/`DELETE /clients/{clientId}/food-restrictions/{restrictionId}`; sem vínculo, ou restrição de outro paciente,
  404. O paciente lê as dele em `GET /me/food-restrictions`. O personal não tem rota (BR-03).

## Plano alimentar (DEV-0081)

- `meal_plan`, `meal_plan_version`, `meal` e `meal_item` (migration V8). O plano é da nutricionista para o paciente
  vinculado: nome (2 a 80), início, observação (até 500) e de 1 a 10 refeições, cada uma com nome (2 a 60), horário
  sugerido opcional (`HH:MM`), observação e até 30 alimentos. O conteúdo fica numa versão desde o rascunho, para a
  edição do publicado gerar outra versão (DEV-0083).
- Item: alimento da TACO ou próprio da autora, sem arquivado (a menos que já estivesse no plano), e quantidade na unidade
  base (0,1 a 5000 g ou ml). Com a medida caseira dela daquele alimento e quantas (0,1 a 100), a quantidade sai da
  medida. Medida apagada depois some do item; a quantidade fica.
- Macros calculados na leitura (`Food.nutrientsIn`): por item, a refeição soma os itens e o dia soma as refeições. A
  fibra some do total quando falta em algum alimento.
- Alternativas (DEV-0082, FR-44, `meal_item_alternative`, migration V9): até 2 por item (a secundária e a terciária),
  cada uma com alimento usável e quantidade própria (0,1 a 5000), sem repetir o principal nem a outra. A leitura traz os
  macros da alternativa e a diferença para o principal (alternativa menos principal, com sinal). Só o principal entra
  na soma da refeição e do dia.
- Rascunho, publicado e substituído: publicar exige alimento em cada refeição (BR-10) e deixa substituído o plano ativo
  anterior do paciente (um ativo por vez, garantido pelo índice `ux_meal_plan_published`).
- Alertas (DEV-0084, FR-41, BR-11): cada item e cada alternativa trazem as alergias e intolerâncias do paciente cujo
  termo aparece no nome do alimento ou de um ingrediente da receita, sem diferenciar maiúsculas nem acentos. A
  preferência não alerta. O alerta é calculado na leitura, com as restrições atuais, só para a autora com vínculo
  ativo; nunca bloqueia salvar nem publicar, e o paciente não o recebe.
- Versões (DEV-0083, FR-46, BR-09): o rascunho muda no lugar; editar o publicado cria a versão seguinte, já publicada,
  e a anterior nunca muda. No publicado, `baseVersion` é obrigatória (400 sem ela); base desatualizada ou plano
  substituído, 409. A gravação é otimista pelo `updated_at` lido, e a do rascunho só vale enquanto ele é rascunho.
  A autora lê `GET /meal-plans/{id}/versions` e `/versions/{n}`; o paciente lê as do plano que recebeu em
  `GET /me/meal-plans/{id}/versions` e `/versions/{n}` (o rascunho nunca aparece).
- Nutricionista com vínculo ativo (BR-02): `GET`/`POST /clients/{clientId}/meal-plans`, `PUT /meal-plans/{id}` e
  `POST /meal-plans/{id}/publish`. `GET /meal-plans/{id}` só para a autora, mesmo sem vínculo (BR-05). Sem vínculo ou
  plano de outra nutricionista, 404; campo fora das regras ou alimento indisponível, 400 com o caminho
  (`meals[1].items[0].foodId`). O personal não tem rota (BR-03).

## Registro de refeição (DEV-0087)

- `GET /me/meal-plan/today` (papel `client`): o plano publicado do paciente, na versão atual, com os macros (sem os
  alertas, que são da nutricionista) e os registros de hoje. Sem plano publicado, 404.
- `PUT /me/meal-logs/{logId}` (FR-50, migration V10): a refeição (`mealPosition`) como `DONE`, `PARTIAL` ou
  `SKIPPED` e as trocas `[{itemPosition, alternative}]` (0 é a secundária, 1 a terciária; o alimento sem troca foi o
  principal). O id vem do app: repetir corrige o mesmo registro. O dia (America/Sao_Paulo) e a versão vêm do servidor;
  uma refeição por dia (outro id, 409). Refeição, alimento ou alternativa que não existem na versão, ou troca na
  refeição não feita, respondem 400 no campo.
- O registro fica na versão em que foi feito (BR-09): a correção é conferida contra ela. Corrigir e apagar
  (`DELETE`) só no dia (BR-08, QUESTÃO-04); depois, 409.
- Só o plano publicado do próprio paciente: o de outro paciente e o rascunho respondem 404, e o substituído não recebe
  registro novo (409). O id de um registro não serve para outro paciente (409 ao corrigir, 404 ao apagar).

## Desvio e foto do prato (DEV-0088)

- O registro aceita `deviation`, o desvio em texto livre até 500 caracteres (FR-51, migration V11), em qualquer estado.
- **Foto do prato**, no mesmo desenho das fotos de evolução do client-service (DEV-0048):
  - fica no SeaweedFS, em bucket privado (`S3_MEAL_PHOTOS_BUCKET`, padrão `fithub-meal-photos`), e só circula por URL
    pré-assinada de 5 minutos;
  - `POST /me/meal-logs/{logId}/photo/upload-url` (JPEG ou PNG) devolve a URL de envio e o `uploadId`;
  - `PUT /me/meal-logs/{logId}/photo` confirma: copia para a chave final, confere tipo e tamanho (até 10 MB) no objeto e
    apaga a foto anterior;
  - `DELETE /me/meal-logs/{logId}/photo` tira a foto;
  - a leitura do registro traz `photoUrl` (5 minutos).
- Só o paciente, só no dia do registro (409 depois). Apagar o registro leva os arquivos. Os envios nunca confirmados
  saem pelo expurgo periódico (`fithub.storage.pending-max-age`, padrão 1 hora).
- A correção do registro e a foto têm gravações separadas: uma não desfaz a outra quando chegam juntas.
- **Configuração:** `S3_ENDPOINT`, `S3_PUBLIC_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` e
  `S3_MEAL_PHOTOS_BUCKET`. Os padrões servem só para o compose local.

## Evolução da dieta (DEV-0092)

- `GET /clients/{clientId}/progress/nutrition` (nutricionista com vínculo ativo; sem vínculo, 404) e
  `GET /me/progress/nutrition` (paciente), com `from` e `to` opcionais: padrão das últimas 4 semanas, até 26 semanas
  (FR-57, FR-58).
- **Aderência:** cada refeição prescrita num dia conta 1.
    - A refeição feita vale 1, a parcial 0,5, e a não feita ou sem registro vale 0.
    - As prescritas de um dia são as refeições da versão do plano em vigor nele, da publicação até a substituição.
    - A série é diária, e o percentual tem uma casa.
- **Alternativas mais usadas:** as 10 mais escolhidas no período, com o alimento e quantas vezes.
- **Desvios:** os registros do período com desvio ou foto, com o texto e a URL temporária da foto (5 minutos).
- A nutricionista vê só os planos dela; o paciente vê todos.
- `GET /professionals/me/clients/activity` (nutricionista): cada paciente com vínculo ativo e o último registro de refeição
  nos planos dela, base da lista de inativos (FR-59).
- Tudo é calculado na leitura, sem tabela derivada. O personal não tem rota (BR-03).


## Assinatura do profissional (DEV-0101)

- Consome o `AssinaturaAlterada` (`account.subscription-changed.v1`, fila `nutrition.account-events`, com fila de
  mensagens mortas) e guarda em `professional_subscription` o `active` e o `validUntil` de cada profissional. O evento
  mais novo por `occurredAt` vence, inclusive fora de ordem.
- Sem assinatura válida (`validUntil` no passado ou, sem ele, `active` falso), criar, editar e publicar plano alimentar
  respondem 403 ("Sua assinatura venceu. Renove para criar ou editar prescrições."). Ler o que já existe continua,
  para o profissional e para o cliente (FR-06, BR-14).
- Profissional de quem nenhum evento chegou não é bloqueado.
