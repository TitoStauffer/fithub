# @fithub/api-types

Tipos TypeScript das APIs dos serviços, gerados do OpenAPI (DEV-0022). O web e o mobile usam o pacote
pelo workspace (`"@fithub/api-types": "workspace:*"`), só com `import type`.

```ts
import type { AccountServicePaths } from '@fithub/api-types';
```

Cada serviço exporta `<Servico>Paths`, `<Servico>Components` e `<Servico>Operations`, no formato do
[openapi-typescript](https://openapi-ts.dev).

## De onde vêm

```
código do serviço ──► openapi/<servico>.json ──► src/<servico>.ts e src/index.ts
          (teste de esqueleto, Java)       (pnpm --filter @fithub/api-types generate)
```

1. **Contrato OpenAPI.** O `ServiceSkeletonContract` de cada serviço (libs/java-testing) compara o
   `/v3/api-docs` vivo com `openapi/<servico>.json`, normalizado com chaves em ordem alfabética.
   OpenAPI desatualizado derruba o job `Java Services` do CI.
2. **Tipos.** `scripts/generate.mjs` gera `src/` a partir dos contratos. O teste do pacote falha se os
   tipos commitados não baterem com a geração, se sobrar arquivo de um contrato que saiu ou se um
   serviço com Dockerfile ficar sem contrato. Isso roda no job `Apps` do CI.

## Quando a API de um serviço muda

Na raiz do monorepo:

```sh
# 1. regrava o contrato do serviço (precisa de Docker: o teste sobe Postgres e RabbitMQ)
mvn -pl services/account-service -am test -Dtest=AccountServiceSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false -Dfithub.openapi.update=true

# 2. gera os tipos
pnpm --filter @fithub/api-types generate
```

Commite o JSON e os tipos no mesmo PR da mudança de API. Os arquivos de `src/` são gerados: não edite
à mão.

O `openapi-typescript` 7.13 declara `typescript ^5`, por isso este pacote usa o TypeScript 5.9 só para a
geração. Os apps continuam no 6.0.
