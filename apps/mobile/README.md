# mobile

App mobile do cliente (DEV-0021): Expo + React Native + TypeScript. Esqueleto: as telas (registro de
série, refeições, avaliações) chegam com as histórias do E1 em diante.

| Peça                                               | Versão             | Observação                                                                                             |
| -------------------------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------ |
| Expo SDK                                           | 58.0               | React Native 0.88 e React 19.3, nas versões que o SDK fixa                                             |
| TypeScript                                         | 6.0                | Mesma linha do app web                                                                                 |
| ESLint + eslint-config-expo                        | 9.39 / 58.0        | A configuração do Expo ainda usa plugins sem suporte ao ESLint 10; volta para a 10 quando ela suportar |
| Jest + jest-expo + React Native Testing Library    | 30.5 / 58.0 / 14.1 | Testes de comportamento; JUnit em `reports/junit.xml` (jest-junit) para a guarda do CI                 |
| expo-auth-session + expo-web-browser + expo-crypto | 58.0 / 58.1 / 58.0 | Login no navegador do sistema, com Authorization Code + PKCE                                           |
| expo-secure-store                                  | 58.0               | Refresh token no cofre do sistema (Keychain / Keystore)                                                |

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
App.tsx                   tela de entrada sem sessão e área do cliente com sessão
src/features/auth/        sessão, login PKCE, cofre do refresh token e tela de entrada
src/lib/apiFetch.ts       fetch autenticado (Bearer, nova tentativa no 401)
src/features/<area>/      telas e chamadas de API por área, com os testes ao lado
```

`features/status` é o exemplo do padrão de tela com dado remoto. Ele consulta o health público do
client-service (`EXPO_PUBLIC_CLIENT_API_URL`, padrão `http://localhost:8082`) e mostra verificando,
disponível ou indisponível, com a ação de tentar de novo. No emulador Android, `localhost` é o próprio
aparelho: aponte a variável para o IP da máquina. A segurança é do backend: o app só exibe o que a API
autoriza.

O workspace nega o script de instalação do `@parcel/watcher`, que vem do Expo e já tem binário
pré-compilado (`allowBuilds` no `pnpm-workspace.yaml`).

## Login e sessão (DEV-0028)

O app usa o client `fithub-mobile` do realm `fithub` (`EXPO_PUBLIC_KEYCLOAK_URL`, padrão
`http://localhost:8080`; suba o Keycloak pelo compose de `infra/`). A decisão dos papéis e dos clients
está no ADR-007.

- **Login:** "Entrar" abre o login do Keycloak no navegador do sistema, com Authorization Code + PKCE
  (S256), e volta para o app pelo esquema `fithub://auth` (`scheme` no `app.json`).
- **O que fica guardado:** só o refresh token, no cofre do sistema (`expo-secure-store`). No iOS, ele
  fica no Keychain, restrito a este aparelho e acessível só com ele desbloqueado. No Android, é cifrado
  com uma chave do Keystore do aparelho; a opção de acesso do iOS não tem efeito ali. O access token fica só em memória. Nada de token em
  `AsyncStorage`, log ou mensagem de erro.
- **Reabrir o app:** a sessão é retomada pelo refresh token (escopo `offline_access`). Refresh token
  recusado pelo Keycloak apaga o cofre e leva à tela de entrada. Sem conexão, a sessão guardada é
  mantida e a tela oferece "Tentar de novo".
- **Sair:** apaga o cofre e revoga a sessão no Keycloak pelo refresh token, sem abrir o navegador.
- **Chamar uma API:** use `apiFetch` de `useSession()`. Ele manda o Bearer token, renova num 401 e só
  aceita as origens de `apiOrigins()` (`features/auth/authConfig.ts`).
- **Testes:** `createSession` recebe um `AuthGateway` e um `TokenStore`; os testes usam os falsos de
  `features/auth/fakes.ts`. Nenhum teste fala com o Keycloak nem com o cofre real.
- **Rodar no aparelho:** o esquema `fithub://` e o cofre exigem um build de desenvolvimento
  (`expo run:android` ou `expo run:ios`); o Expo Go não registra o esquema do app.
