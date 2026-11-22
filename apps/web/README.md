# web

App web do profissional (personal e nutricionista), DEV-0020. Esqueleto: as telas chegam com as
histórias do E1 em diante.

| Peça                                  | Versão             | Para quê                                                                         |
| ------------------------------------- | ------------------ | -------------------------------------------------------------------------------- |
| React + Vite + TypeScript             | 19.3 / 8.3 / 6.0   | App e build                                                                      |
| TanStack Query                        | 5.107              | Dados do servidor: cache, estados de carregando e erro, nova tentativa           |
| keycloak-js                           | 26.2               | Login (Authorization Code + PKCE), renovação de token e logout                   |
| React Router                          | 8.5                | Rotas e rota protegida por papel                                                 |
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
├── main.tsx              QueryClientProvider, AuthProvider, roteador e montagem
├── App.tsx               rotas: início público e /app protegida por papel
├── components/ui/        componentes do shadcn/ui (button)
├── features/<area>/      telas e chamadas de API por área, com os testes ao lado
├── features/auth/        sessão com o Keycloak, rota protegida e useApiFetch
├── lib/config.ts         URLs do Keycloak e das APIs (variáveis VITE_*)
├── lib/http.ts           fetch autenticado (Bearer, nova tentativa no 401)
├── lib/utils.ts          cn() para classes do Tailwind
└── test/setup.ts         matchers do jest-dom e limpeza entre testes
```

`features/status` é o exemplo do padrão de dados. Ele consulta o health público do account-service
(`VITE_ACCOUNT_API_URL`, padrão `http://localhost:8081`) e mostra carregando, disponível ou indisponível,
com a ação de tentar de novo. A segurança é do backend: o front só exibe o que a API autoriza.

## Login e sessão (DEV-0027)

O app usa o client `fithub-web` do realm `fithub` (`VITE_KEYCLOAK_URL`, padrão `http://localhost:8080`;
suba o Keycloak pelo compose de `infra/`). A decisão dos papéis e dos clients está no ADR-007.

- **Rotas:** `/` é pública; `/app` exige sessão e o papel `personal` ou `nutritionist`. Sem sessão, a
  rota leva ao login e volta para a página pedida. Sem o papel, mostra acesso negado.
- **Tokens só em memória:** nada de token em `localStorage` ou `sessionStorage`. Para chamar uma API,
  use `useApiFetch()`: ele manda o Bearer token, renova num 401 e só aceita as origens de `apiOrigins()`.
- **Nova API do FitHub:** acrescente a origem em `apiOrigins()` (`lib/config.ts`); sem isso a chamada
  autenticada é recusada antes de sair.
- **Testes:** use `fakeAuthClient()` (`features/auth/fakeAuthClient.ts`) no `<AuthProvider>`; nenhum
  teste fala com o Keycloak.
- **Conta de teste local:** crie o usuário no console do Keycloak e atribua o papel `personal` pelo
  console, até o cadastro do profissional existir (DEV-0029).
