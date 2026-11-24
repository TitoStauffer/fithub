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
