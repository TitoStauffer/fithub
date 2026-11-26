# Aprendizados — contratos de API (OpenAPI)

Contrato HTTP gerado pelo springdoc e os tipos de `packages/api-types` que a web e o mobile consomem.

## resposta-documentada-sem-content-herda-o-retorno

**Regra:** num `@ApiResponse` sem `content`, o springdoc infere o corpo pelo tipo de retorno do método. Um
404 ou outro erro declarado só com `responseCode` e `description` vira, no contrato, uma resposta com o corpo
de sucesso. Toda resposta documentada declara o próprio `content`. Erro responde Problem Details
(`application/problem+json`) com o schema do serviço que descreve o JSON real (`Problem`, com `errors[]`),
não a classe `ProblemDetail` do Spring. Resposta sem corpo usa `content = @Content()`.

### Ocorrências

- **Revisão:** Code Review do PR #202, no SHA `39f9235` (MEDIUM: o 404 do `GET /professionals/me` aparecia
  como `Professional` em `application/json`; LOW: o schema `ProblemDetail` descrevia um objeto `properties`
  e não trazia `errors[]`) — DEV-0031 (#23) · **PR:** #202
  - **Impacto:** contrato público falso para a web e o mobile: o tipo gerado dizia que o 404 trazia um
    cadastro, e o critério "Problem Details nos erros" não era atendido.
  - **Correção:** o 404 passou a ser Problem Details (`ErrorResponseException`), declarado com o schema
    `Problem` (record `ApiProblem`), que também substituiu o `ProblemDetail` nas demais respostas de erro.
    O mesmo defeito, nos `GET` de perfil e de saúde do client-service (DEV-0032 e DEV-0033), foi corrigido
    nos PRs dessas tarefas.
  - **Prevenção:** o teste do 404 confere `application/problem+json` e o `detail`; o mutante M11 de
    `mutations/DEV-0031.json` volta o 404 sem a mensagem e é derrubado. Ao revisar um controller, conferir
    no OpenAPI gerado que cada código de resposta tem o corpo certo.

## nulo-sobre-ref-no-openapi

**Regra:** `@Schema(nullable = true)` num campo cujo tipo vira `$ref` (um record ou objeto) faz o springdoc 3.1
gerar `{"$ref": ..., "type": "null"}`. No JSON Schema 2020-12 as palavras irmãs do `$ref` valem juntas, então
nenhum valor satisfaz o schema, e o tipo TypeScript gerado fica sem `| null`. Campo de objeto que pode faltar
é omitido do JSON (`@JsonInclude(JsonInclude.Include.NON_NULL)` no componente) e declarado só como opcional;
o teste prova a ausência no corpo, não com `jsonPath(...).doesNotExist()`, que também passa com `null`.

### Ocorrências

- **Revisão:** Code Review do PR #235, no SHA `b8f5ce1` (MEDIUM: `Exercise.attribution` saía como
  `$ref` + `type: null`, e a API devolvia `attribution: null` em exercício próprio) — DEV-0053 (#45) ·
  **PR:** #235
  - **Impacto:** contrato público impossível para a web (a tela do catálogo, DEV-0057, consome o endpoint):
    um validador recusaria toda resposta, e o tipo gerado não admitia o `null` que chegava.
  - **Correção:** sem `nullable`; o campo sai com `@JsonInclude(NON_NULL)`, o contrato traz só o `$ref` e o
    tipo opcional diz a verdade.
  - **Prevenção:** o teste confere que o corpo do exercício próprio não contém `attribution`; o mutante M9
    de `mutations/DEV-0053.json` tira o `@JsonInclude` e é derrubado. Ao revisar o OpenAPI gerado, procurar
    `"type": "null"` ao lado de `$ref`.
- **Revisão:** Code Review CHANGES_REQUESTED do PR #303, no SHA `8aaf142` (MEDIUM: a exportação devolvia
  `profile`, `health`, `consent`, `endedAt`, `endedBy` e `professionalName` como `null`, e o contrato não admitia) —
  DEV-0104 (#96) · **PR:** #303
  - **Impacto:** o app (DEV-0168) receberia `null` em campos que o tipo gerado diz que não são nulos.
  - **Correção:** `@JsonInclude(NON_NULL)` nos records da exportação; o campo que pode faltar some do corpo.
  - **Prevenção:** os testes conferem que o corpo não tem `null` nem os campos ausentes; mutantes M8 e M9 de
    `mutations/DEV-0104.json` tiram o `@JsonInclude`. Ao criar um DTO com `Optional` ou `.orElse(null)`, aplicar
    a regra desde o início.

## operation-id-explicito-quando-o-metodo-repete

**Regra:** o springdoc usa o nome do método Java como `operationId` e, quando dois controllers têm métodos com o mesmo
nome, numera o segundo (`remove_1`, `view_1`). Uma rota nova pode então tomar o `operationId` de uma antiga e mudar o
significado de `operations["remove"]` nos tipos gerados. Rota nova cujo nome de método já existe no serviço declara
`@Operation(operationId = ...)`.

### Ocorrências

- **Revisão:** Code Review do PR #249, no SHA `430bccc` (LOW: `remove` passou a ser o DELETE de série e o DELETE de
  restrição virou `remove_1`) — DEV-0066 (#58) · **PR:** #249
  - **Impacto:** sem quebra no typecheck dos apps, mas `operations["remove"]` trocou de rota no pacote de tipos.
  - **Correção:** `operationId` explícito nas rotas novas (`viewSession`, `recordSet`, `removeSet`); a restrição
    voltou a ser `remove`.
  - **Prevenção:** ao regenerar o contrato, conferir no diff do OpenAPI se algum `operationId` existente mudou.
