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
