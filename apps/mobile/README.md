# mobile

App mobile do cliente (DEV-0021): Expo + React Native + TypeScript. Esqueleto: as telas (registro de
série, refeições, avaliações) chegam com as histórias do E1 em diante.

| Peça                                            | Versão             | Observação                                                                                             |
| ----------------------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------ |
| Expo SDK                                        | 58.0               | React Native 0.88 e React 19.3, nas versões que o SDK fixa                                             |
| TypeScript                                      | 6.0                | Mesma linha do app web                                                                                 |
| ESLint + eslint-config-expo                     | 9.39 / 58.0        | A configuração do Expo ainda usa plugins sem suporte ao ESLint 10; volta para a 10 quando ela suportar |
| Jest + jest-expo + React Native Testing Library | 30.5 / 58.0 / 14.1 | Testes de comportamento; JUnit em `reports/junit.xml` (jest-junit) para a guarda do CI                 |

## Comandos

Na raiz do monorepo (Node 24, pnpm do `packageManager`):

```sh
pnpm install
pnpm --filter mobile start       # Expo: abra no Expo Go pelo QR code
pnpm --filter mobile lint        # ESLint (sem avisos)
pnpm --filter mobile typecheck
pnpm --filter mobile test        # Jest; JUnit em reports/junit.xml
```

O CI (job `Apps`) roda lint, typecheck, testes e a guarda de teste pulado. Teste pulado precisa da issue
no nome: `it.skip('motivo DEV-xxxx', ...)`.

## Estrutura

```
index.ts                  registerRootComponent
App.tsx                   casca do app
src/features/<area>/      telas e chamadas de API por área, com os testes ao lado
```

`features/status` é o exemplo do padrão de tela com dado remoto. Ele consulta o health público do
client-service (`EXPO_PUBLIC_CLIENT_API_URL`, padrão `http://localhost:8082`) e mostra verificando,
disponível ou indisponível, com a ação de tentar de novo. No emulador Android, `localhost` é o próprio
aparelho: aponte a variável para o IP da máquina. A segurança é do backend: o app só exibe o que a API
autoriza.

O workspace nega o script de instalação do `@parcel/watcher`, que vem do Expo e já tem binário
pré-compilado (`allowBuilds` no `pnpm-workspace.yaml`).
