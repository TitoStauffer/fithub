# Aprendizados — idempotência e posse de registros

Registros com id gerado pelo cliente (app), gravados por upsert para não duplicar no toque duplo.

## upsert-pelo-id-do-cliente-confere-dono-e-lugar

**Regra:** quando o id vem do cliente e o upsert corrige o registro existente, a correção só vale para o mesmo dono e
o mesmo lugar. O id de um registro de outra sessão (do próprio usuário ou de outro) não pode ser corrigido nem
apagado pelo caminho de outra; e a correção não pode mudar o registro de lugar (item, número) nem gravar dados de
outro lugar. A guarda fica na aplicação (com mensagem clara) e no próprio upsert (`on conflict (id) do update ...
where tabela.dono = excluded.dono`, tratando 0 linhas como conflito), e cada guarda tem teste que falha sem ela: o
teste da aplicação usa um id de outra sessão, e o do banco chama o repositório direto, porque a aplicação já barra o
caso antes.

### Ocorrências

- **Revisão:** Code Review do PR #249, no SHA `430bccc` (MEDIUM: corrigir a série pelo mesmo id com outro
  `itemPosition` gravava o exercício do outro item na série antiga; MEDIUM: as guardas de série de outra sessão no
  PUT e no DELETE não tinham teste nem mutante; LOW: upsert sem restringir a sessão) — DEV-0066 (#58) · **PR:** #249
  - **Impacto:** registro de execução do aluno (BR-01) incoerente, lendo errado no pré-preenchimento e nos recordes;
    sem teste, a guarda que impede corrigir ou apagar a série de outro aluno pelo id podia sumir sem ninguém ver.
  - **Correção:** corrigir pelo mesmo id com outro item ou número responde 409; o upsert só atualiza dentro da mesma
    sessão (0 linhas = 409); testes com id de outra sessão do próprio aluno e de outro aluno no PUT (409) e no DELETE
    (404), e o teste do repositório para a guarda do banco.
  - **Prevenção:** mutantes M7 a M11 de `mutations/DEV-0066.json`, um para cada guarda.
- **Revisão:** Code Review do PR #255, no SHA `6526080` (MEDIUM: no app, corrigir uma série já registrada mandava
  o exercício escolhido na tela, e não o que a série gravou; a série feita com a alternativa virava do exercício do
  item, e vice-versa) — DEV-0071 (#63) · **PR:** #255
  - **Impacto:** a correção de carga ou repetições trocava o exercício da série sem o aluno pedir, distorcendo o
    pré-preenchimento e os recordes por exercício (FR-53 a FR-55) que o personal vinculado lê (BR-01).
  - **Correção:** o cliente também respeita a regra: a correção manda o exercício que a série gravou
    (`recorded.exerciseId`) e mostra o nome dele; a escolha da alternativa só vale para as séries novas.
  - **Prevenção:** teste que corrige a série feita com a alternativa (e o inverso) e confere o `exerciseId` enviado;
    mutante M6 de `mutations/DEV-0071.json`.

## gravacao-por-fluxo-nao-regrava-a-linha-inteira

**Regra:** quando dois fluxos mexem na mesma linha (finalizar o treino e comentar o treino, por exemplo), cada um
grava só as suas colunas. Um upsert que regrava a linha inteira com o que leu antes perde a atualização do outro fluxo
que chegou no meio: pode desfazer uma finalização ou apagar um comentário que já respondeu 200. Estado que só avança
(finalizado) usa `coalesce` para nunca voltar. O teste grava pelo repositório um objeto lido antes da outra
atualização e confere que ela continua lá.

### Ocorrências

- **Revisão:** Code Review do PR #250, no SHA `a8c1bce` (MEDIUM: o upsert da sessão passou a regravar
  `finished_at`, `comment` e `feeling` juntos; comentar e finalizar ao mesmo tempo podiam reabrir o treino ou apagar o
  comentário) — DEV-0067 (#59) · **PR:** #250
  - **Impacto:** treino finalizado voltando a aberto (a sequência não avança, FR-29) e comentário do aluno perdido
    depois de um 200 (BR-01).
  - **Correção:** o comentário e a sensação têm gravação própria (`saveFeedback`); o upsert da sessão só preenche
    `finished_at`, com `coalesce` para nunca desfazer a finalização.
  - **Prevenção:** teste com a sessão desatualizada gravada depois da finalização; mutantes M6, M7 e M9 de
    `mutations/DEV-0067.json`.
