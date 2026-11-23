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
