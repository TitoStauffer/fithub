# Aprendizados — migrations e constraints

Esquema, CHECKs, unicidade e migrations Flyway.

## check-com-in-em-coluna-anulavel

**Regra:** num CHECK, `coluna IN (...)` sobre coluna que aceita nulo não recusa o nulo: `NULL IN ('G', 'P')`
dá NULL, e o Postgres só recusa a linha quando o CHECK dá falso. Quando o valor é obrigatório naquele
caso, escreva `coluna IS NOT NULL AND coluna IN (...)`, e teste a constraint com escrita direta no banco
(sem passar pelo domínio).

### Ocorrências

- **Revisão:** Code Review do PR #199, no SHA `58383ec` (LOW: `ck_professional_council` aceitava PERSONAL sem categoria) — DEV-0029 (#21) · **PR:** #201 (correção, DEV-0030) e #202 (registro do aprendizado, pedido no Code Review do #201)
  - **Impacto:** o banco aceitava um personal sem a categoria do CREF. O domínio recusava antes, então não havia dado errado, mas qualquer escrita fora do domínio (script, migration, correção manual) passaria.
  - **Correção:** a V3 do account-service refez o CHECK com `registration_category is not null and registration_category in ('G', 'P')`.
  - **Prevenção:** o teste `bancoRecusaPersonalSemCategoriaMesmoPorEscritaDireta` grava direto na tabela e espera a recusa; o mutante M5 de `mutations/DEV-0030.json` volta o CHECK antigo e é derrubado.
- **Revisão:** Code Review do PR #228 em 23/11/2026 (MEDIUM) — DEV-0045 (#37) · **PR:** #228
  - **Impacto:** a faixa das dobras cutâneas (1 a 80 mm, 1 casa decimal) não tinha teste nem no domínio nem nas
    CHECKs novas da V9 (`ck_body_assessment_skinfolds` e `ck_body_assessment_skinfolds_presential`), e o PR citava
    o `AssessmentTest` como evidência sem ele cobrir a faixa. Remover a guarda não derrubaria nenhum teste.
  - **Correção:** casos parametrizados para cada dobra em `AssessmentTest`, um teste de API com 400 no campo e
    `bancoRecusaDobraForaDaFaixaEDobraNaOnlineMesmoPorEscritaDireta`.
  - **Prevenção:** mutantes M7 a M10 de `mutations/DEV-0045.json`. Antes de citar um teste como evidência de um
    critério, conferir que ele falha sem a guarda: CHECK nova sempre leva teste de escrita direta no banco.
- **Revisão:** Code Review do PR #261, no SHA `682113f` (MEDIUM) — DEV-0077 (#69) · **PR:** #261
  - **Impacto:** guardas novas sem teste nem mutante, com o PR afirmando o contrário:
    `ck_food_recipe_ingredient_position` e a faixa do rendimento em `ck_food_recipe` (V5); os limites de
    ingredientes, de quantidade e de rendimento e o ingrediente sem alimento (`Recipe`); a receita só em gramas
    e o arquivamento idempotente (`Food`). Remover qualquer uma delas não derrubaria nenhum teste.
  - **Correção:** escrita direta para a posição 30 e os rendimentos 0,5 e 20000,1; casos de domínio para cada
    limite e para a receita em ml; o arquivamento repetido conferindo a data no banco.
  - **Prevenção:** mutantes M8 a M18 de `mutations/DEV-0077.json`. A regra vale para toda guarda, não só para
    CHECK: antes do PR, listar cada condição nova (CHECK, faixa, limite, regra de posse) e conferir que o
    manifesto tem um mutante dela; a seção de banco do PR só afirma "cada CHECK tem teste" depois disso.
- **Revisão:** Code Review do PR #265, no SHA `5ec51aa` (MEDIUM) — DEV-0080 (#72) · **PR:** #265
  - **Impacto:** a guarda de vínculo do `PUT` da restrição alimentar (dado de saúde) não tinha teste nem mutante:
    sem ela, uma nutricionista sem vínculo editaria a restrição do paciente e nenhum teste falharia (BR-02). Outras
    guardas tinham teste mas não mutante, de novo com o PR afirmando o contrário.
  - **Correção:** teste do `PUT` sem vínculo e um mutante por guarda: o vínculo em cada operação (list, create,
    update, remove), cada faixa (termos, descrição), o tipo vazio e cada CHECK (M9 a M19 de
    `mutations/DEV-0080.json`).
  - **Prevenção:** a guarda repetida em vários métodos (o `requireActiveLink` em cada operação) conta como uma guarda
    por método: cada uma tem o seu teste (a mesma chamada sem vínculo, por verbo) e o seu mutante.
