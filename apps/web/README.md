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
  rota leva ao login e volta para a página pedida. Sem o papel, leva ao `/onboarding` (DEV-0031).
- **Onboarding (DEV-0031):** `/onboarding` exige só a sessão. Sem cadastro de profissional, mostra o
  formulário (tipo e registro CREF/CRN com UF), com a validação da API repetida no navegador; depois
  do cadastro, `reloadSession()` pede um token novo para trazer o papel e a pessoa entra em `/app`. Com
  o cadastro feito e o papel ainda pendente no Keycloak, a tela oferece "Entrar na área do
  profissional" para tentar de novo.
- **Falhas de sessão (DEV-0146):** só a recusa do Keycloak encerra a sessão. Sem rede, a sessão
  continua e o token atual é usado enquanto valer; sem token válido, `getAccessToken` lança
  `SessionUnavailableError` (mostre "sem conexão" e deixe tentar de novo). Se a API recusar até o
  token renovado, a rota protegida mostra "Sair e entrar de novo" em vez de redirecionar.
- **Tokens só em memória:** nada de token em `localStorage` ou `sessionStorage`. Para chamar uma API,
  use `useApiFetch()`: ele manda o Bearer token, renova num 401 e só aceita as origens de `apiOrigins()`.
- **Nova API do FitHub:** acrescente a origem em `apiOrigins()` (`lib/config.ts`); sem isso a chamada
  autenticada é recusada antes de sair.
- **Testes:** use `fakeAuthClient()` (`features/auth/fakeAuthClient.ts`) no `<AuthProvider>`; nenhum
  teste fala com o Keycloak.
- **Conta de teste local:** crie o usuário no console do Keycloak e atribua o papel `personal` pelo
  console, até o cadastro do profissional existir (DEV-0029).

## Clientes e convites (DEV-0043)

- **`/app`:** clientes com vínculo ativo (`GET /professionals/me/clients`); cada um leva a `/app/clientes/:clientId`,
  com o perfil e a saúde vindos da API (DEV-0040). Quem decide o acesso é a API: com o vínculo encerrado, a
  próxima leitura volta 404 e a tela mostra que o acesso acabou (PRD §14).
- **Encerrar o vínculo:** pede confirmação na própria tela e volta para a lista.
- **`/app/convites`:** convida por e-mail ou gera link e código para compartilhar (só aparecem na resposta da
  criação); lista e cancela os convites pendentes; mostra e troca o código pessoal.
- **Configuração:** `VITE_CLIENT_API_URL` (padrão `http://localhost:8082`); a origem entra em `apiOrigins()`.

## Avaliação presencial e histórico (DEV-0049)

- Na tela do cliente vinculado, o histórico de avaliações mostra cada % de gordura com a origem (Pollock 3 e 7
  dobras, Marinha, bioimpedância), porque os métodos não são comparáveis (FR-19).
- **`/app/clientes/:clientId/avaliacoes/nova`:** avaliação presencial com peso, circunferências, dobras, sexo para as
  fórmulas e bioimpedância, com a validação da API espelhada. O cálculo do % é da API. Vínculo encerrado ao salvar
  mostra que o acesso acabou.

## Comparativo de fotos (DEV-0051)

- Na tela do cliente vinculado com duas avaliações ou mais, o profissional escolhe duas datas e a pose e vê as fotos
  lado a lado (FR-20). A URL pré-assinada de cada foto é pedida na hora de mostrar, sem cache; se a imagem não
  carregar (URL vencida), a tela pede uma nova uma vez e, se falhar de novo, avisa. Sem vínculo, a API responde 404 e
  a tela mostra que o acesso acabou.

## Catálogo de exercícios (DEV-0057)

- **`/app/exercicios`** (só para o papel `personal`; a nutricionista não vê o link nem a página): busca por nome com
  filtros de grupo muscular e equipamento, em páginas de 20 com o total (training-service, DEV-0055). Mostra
  carregando, vazio e erro com "Tentar de novo".
- Cada exercício mostra grupos, equipamento e instruções. A mídia é um link que abre em outra aba
  (`rel="noopener noreferrer"`), sem player no MVP. O importado mostra a fonte, a licença e o autor do texto e,
  quando há mídia, a licença e o autor dela (ADR-010).
- **Exercício próprio:** `/app/exercicios/novo` e `/app/exercicios/:id/editar`, com a validação da API espelhada
  (nome de 2 a 120, ao menos um grupo, equipamento, instruções até 4000, mídia só `https`) e o erro de campo da API
  no campo. Arquivar pede confirmação na própria tela; o arquivado sai da busca, e editá-lo responde o aviso da API.
- **Configuração:** `VITE_TRAINING_API_URL` (padrão `http://localhost:8083`); a origem entra em `apiOrigins()`.

## Editor de ficha de treino (DEV-0063)

- Na tela do aluno vinculado, a seção **Fichas de treino** (só para o papel `personal`) lista as fichas com o estado
  (rascunho, publicada, substituída), a versão e o vencimento, e leva ao editor.
- **Editor** (`/app/clientes/:clientId/fichas/nova` e `.../fichas/:planId`): nome, início, validade e até 7 treinos
  (A a G), com nome opcional. Os exercícios vêm da busca do catálogo, com séries, repetições (fixas ou faixa), carga,
  descanso, técnica, observação e até 3 alternativas; a ordem muda por subir e descer.
- A validação da API fica espelhada no navegador, e o erro de campo da API aparece no item (`divisions[i].items[j]...`).
  O alerta de restrição aparece no item e não impede salvar nem publicar (BR-11).
- **Publicar** pede confirmação e exige o rascunho salvo e um exercício em cada treino. Na ficha publicada, a tela avisa
  que salvar cria a versão seguinte e manda a versão base; se outra edição chegou antes (409), a ficha é recarregada com
  aviso. Vínculo encerrado mostra que o acesso acabou.

## Restrições de mobilidade e fichas vencendo (DEV-0064)

- Na tela do aluno vinculado, a seção **Restrições de mobilidade** (só para o papel `personal`) lista as restrições e
  permite adicionar (região do corpo + descrição de 1 a 500 caracteres), editar e remover com confirmação, com a
  validação da API espelhada e o erro de campo dela. A ficha alerta quando um exercício envolve uma dessas regiões.
- **`/app/fichas-vencendo`** (link na navegação, só para o personal): as fichas publicadas dos alunos vinculados que
  vencem nos próximos 7 dias ou já venceram, com aluno, ficha e data, as vencidas marcadas; cada uma leva ao aluno.
- A nutricionista não vê nenhuma das duas (BR-03): quem não é personal recebe "Esta área é só para o personal.".
