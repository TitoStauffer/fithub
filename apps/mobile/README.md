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

## Perfil, saúde, convite e vínculos (DEV-0041, DEV-0042)

- **Depois do login:** sem perfil, o app abre o cadastro; com perfil, a tela inicial mostra o nome e a idade
  calculada pela API, com acesso a editar o perfil, o perfil de saúde, aceitar convite e ver os vínculos.
- **Formulários:** a validação espelha a do client-service (`features/profile/profileForm.ts`). Erro da API vai
  para o campo; campo que a tela não mostra vai para o aviso geral. Um envio por vez. Sem conexão
  (`OfflineError`), o app avisa e mantém o que foi digitado.
- **Saúde:** fica só no estado da tela aberta; nunca vai para o cofre, `AsyncStorage` ou log.
- **Convite:** o link `fithub://convite/<token>` abre a tela de aceite (React Native `Linking`); também dá para
  digitar o código do convite ou o código pessoal do profissional. A tela mostra o que será compartilhado e a
  versão do termo, e só aceita com a confirmação marcada. Recusar não chama a API.
- **Vínculos:** lista ativos e encerrados; encerrar pede confirmação na própria tela.

## Avaliação online e fotos (DEV-0050)

- **Passo a passo:** peso e sexo para as fórmulas, depois uma tela por circunferência com a instrução de como medir
  (`features/assessments/onlineSteps.ts`). Pescoço, cintura e quadril vêm antes porque alimentam a estimativa da
  Marinha. Qualquer medida pode ser pulada; se faltar uma que a fórmula usa, a revisão avisa que o % estimado não
  será calculado. A validação espelha a do client-service (10 a 300 cm, peso de 20 a 400 kg).
- **Resultado:** o % estimado aparece marcado como online (fórmula da Marinha), ou o motivo de não ter saído.
- **Fotos:** frente, lado e costas, pela câmera (`expo-image-picker`). Cada foto vai direto para a URL pré-assinada
  do client-service, sem o token da sessão, e é confirmada na API. O arquivo é apagado do aparelho
  (`expo-file-system`) logo depois da tentativa, com ou sem sucesso.
- **Sem conexão:** o envio da avaliação avisa e mantém tudo o que foi preenchido; a foto é descartada e a tela pede
  para tirar de novo. Medidas ficam só no estado da tela, nunca no cofre, `AsyncStorage` ou log.

## Comparativo de fotos (DEV-0051)

- "Comparar fotos" na tela inicial: o cliente escolhe duas datas e a pose e vê as próprias fotos lado a lado. A URL de
  cada foto é pedida na hora de mostrar e pedida de novo uma vez se a imagem não carregar (URL vencida).

## Treino de hoje (DEV-0070)

- "Treino de hoje" na tela inicial: cada ficha ativa (a do personal e, se houver, a própria) com o próximo treino da
  sequência e cada exercício com séries e repetições (ou a faixa), carga sugerida, descanso, técnica, observação e
  alternativas. A mídia abre fora do app, pelo link; o exercício importado mostra o crédito (fonte, licença e autor).
- Ficha vencida mostra o aviso e continua disponível; sem ficha, a tela convida a pedir ao personal.
- "Começar treino" inicia a sessão com um id gerado no aparelho (`expo-crypto`), então o toque duplo não abre duas;
  com sessão aberta, a ficha mostra "Continuar treino". O registro das séries é a DEV-0071.
- O app fala com o training-service em `EXPO_PUBLIC_TRAINING_API_URL` (padrão `http://localhost:8083`), que passa a
  receber o token junto com o client-service.
- Sem conexão, a tela avisa e mantém o que já estava carregado.

## Registro por série (DEV-0071)

- "Começar" ou "Continuar treino" abre o registro: cada série vem preenchida com o último treino do exercício (a mesma
  série, ou a última feita) ou, sem ele, com o prescrito (na faixa 8–12, o mínimo; sem carga sugerida, 0). "Confirmar"
  grava com um toque; os campos permitem ajustar antes.
- O id da série nasce no aparelho no primeiro toque e vale para o toque duplo e os reenvios: nunca duplica.
- "Trocar por" uma alternativa prevista muda o exercício das próximas séries do item, preenchidas com o último treino
  da alternativa; a série grava o exercício feito.
- Comentário (até 500 caracteres) e sensação de 1 a 5 antes de "Finalizar treino", que avança a sequência e volta ao
  treino de hoje já com o próximo.
- No mesmo dia, a série registrada pode ser corrigida ou apagada; no dia seguinte, a tela avisa e não deixa registrar.
- Sem conexão, avisa e mantém o que foi digitado (sem modo offline no MVP, PRD §14).

## Histórico e recordes (DEV-0072)

- "Histórico de treinos": os treinos finalizados, do mais novo para o mais antigo, com data, ficha, treino, séries e
  sensação, e "Carregar mais" de 20 em 20. O detalhe mostra cada exercício com as séries (e a alternativa usada), o
  comentário e a sensação.
- "Recordes": por exercício, a maior carga e o 1RM estimado, com a marca, a data e a origem (registrada no treino ou
  lançada à mão). "Lançar recorde" busca o exercício no catálogo e pede carga, repetições e data (dd/mm/aaaa, hoje
  por padrão); o erro de campo da API aparece no campo, e o digitado fica.

## Minha ficha (DEV-0073)

- "Minha ficha" no menu, ou "Montar minha ficha" no treino de hoje sem ficha: o aluno monta a ficha própria (DEV-0069)
  com nome, início, validade e até 7 treinos, cada um com exercícios do catálogo (busca pelo nome), séries, repetições
  ou faixa, carga e descanso. Sem técnica nem alternativas no app (a API aceita).
- A validação espelha a da API e o erro aparece no campo do item, inclusive o que a API devolver.
- "Salvar ficha" grava o rascunho; "Publicar" exige exercício em cada treino, pede confirmação e a ficha passa a
  aparecer no treino de hoje. Na ficha publicada, a tela avisa que salvar gera a versão seguinte.

## Refeições de hoje (DEV-0089)

- "Refeições de hoje", na tela inicial, mostra o plano alimentar publicado (`GET /me/meal-plan/today`, DEV-0087):
    - cada refeição na ordem, com o horário e a observação;
    - os alimentos com a quantidade ou a medida caseira (ex.: "2 Concha (50 g)");
    - as alternativas, com a diferença de macros para o principal;
    - os macros da refeição e do dia;
    - o registro de hoje de cada refeição (feita, parcial, não feita ou ainda não registrada).
- Sem plano publicado, a tela avisa. Sem conexão, avisa e deixa tentar de novo.
- O app fala com o nutrition-service em `EXPO_PUBLIC_NUTRITION_API_URL` (padrão `http://localhost:8084`), que passa a
  receber o token junto com o client-service e o training-service.

## Registrar refeição (DEV-0090)

- Em "Refeições de hoje", cada refeição tem "Registrar refeição" (ou "Corrigir registro", quando já tem registro hoje):
    - como foi: feita, parcial ou não feita;
    - em cada alimento com alternativa, o que foi comido (o principal ou uma alternativa); a refeição não feita vai sem
      alternativa;
    - o desvio em texto (até 500 caracteres) e a foto do prato pela câmera ou pela galeria.
- O id do registro vem do aparelho (`expo-crypto`) e é reaproveitado: o reenvio sem conexão e a correção do mesmo dia
  não duplicam. Depois do dia, o 409 da API aparece como aviso.
- A foto vai direto à URL pré-assinada, sem o token. A foto tirada pela câmera é apagada do aparelho depois do envio; a
  da galeria é da pessoa e fica.

## Minha evolução (DEV-0096)

- "Minha evolução", na tela inicial, mostra no período de 4, 8 ou 12 semanas até hoje:
    - **treino:** a aderência (feitos contra esperados) e, do exercício escolhido entre os que têm treino no período,
      a maior carga e o volume de cada treino, com o atalho "Ver recordes";
    - **dieta:** a aderência (refeições feitas contra prescritas, a parcial vale meia);
    - **corpo**, de todo o histórico: o primeiro valor, o último e a variação do peso, de cada circunferência e do
      % de gordura de cada método, sem comparar métodos entre si (BR-12).
- Sem ficha, sem plano ou sem avaliação, o bloco diz o que falta. O erro de um bloco não derruba os outros, e
  "Tentar de novo" recarrega.
