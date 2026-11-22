# web

App web do profissional (personal e nutricionista), DEV-0020. Esqueleto: as telas chegam com as
histórias do E1 em diante.

| Peça                                  | Versão             | Para quê                                                                         |
| ------------------------------------- | ------------------ | -------------------------------------------------------------------------------- |
| React + Vite + TypeScript             | 19.3 / 8.3 / 6.0   | App e build                                                                      |
| TanStack Query                        | 5.107              | Dados do servidor: cache, estados de carregando e erro, nova tentativa           |
| shadcn/ui (Tailwind 4)                | —                  | Componentes copiados para `src/components/ui`, configurados em `components.json` |
| ESLint + typescript-eslint + Prettier | 10.14 / 8.72 / 3.9 | Lint com tipos, hooks do React e formatação                                      |
| Vitest + React Testing Library        | 5.1 / 16.3         | Testes de comportamento, em jsdom                                                |

O TypeScript fica na linha 6.0 porque o typescript-eslint 8.72 aceita até `<6.1`.

## Comandos

Na raiz do monorepo (Node 24, pnpm do `packageManager`):

```sh
pnpm install
pnpm --filter web dev         # http://localhost:5173
pnpm --filter web lint        # ESLint (sem avisos) e Prettier
pnpm --filter web typecheck
pnpm --filter web test        # Vitest; JUnit em reports/junit.xml para a guarda do CI
pnpm --filter web build       # typecheck e build em dist/
```

O CI (job `Apps`) roda lint, typecheck, testes, build e a guarda de teste pulado. Teste pulado precisa
da issue no nome: `it.skip('motivo DEV-xxxx', ...)`.

## Estrutura

```
src/
├── main.tsx              QueryClientProvider e montagem
├── App.tsx               casca do app
├── components/ui/        componentes do shadcn/ui (button)
├── features/<area>/      telas e chamadas de API por área, com os testes ao lado
├── lib/utils.ts          cn() para classes do Tailwind
└── test/setup.ts         matchers do jest-dom e limpeza entre testes
```

`features/status` é o exemplo do padrão de dados. Ele consulta o health público do account-service
(`VITE_ACCOUNT_API_URL`, padrão `http://localhost:8081`) e mostra carregando, disponível ou indisponível,
com a ação de tentar de novo. A segurança é do backend: o front só exibe o que a API autoriza.
