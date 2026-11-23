# Aprendizados — identidade e acesso

Keycloak, realm, clients, papéis e login.

## destino-de-redirecionamento-fixado-por-valor

**Regra:** guarda de configuração de segurança (redirect URI, origem, host de callback) é testada por
**valor exato** (lista permitida), não por formato. Um teste que só recusa o formato ruim (curinga)
aceita qualquer valor bem formado, inclusive o de um terceiro.

### Ocorrências

- **Revisão:** Code Review do PR #190, no SHA `568d460` (LOW: host do `fithub-web` não fixado) — DEV-0024 (#16) · **PR:** #191
  - **Impacto:** trocar `http://localhost:5173/*` por um host fixo de terceiro nas redirect URIs do `fithub-web` não derrubava nenhum teste nem mutante; o código de autorização poderia ser entregue a outro site sem o CI acusar. No mobile o valor já era fixado.
  - **Correção:** `realm.test.mjs` compara redirect URIs, origens, logout e `rootUrl` do web e do mobile com `deepEqual`.
  - **Prevenção:** mutantes M9 e M10 de `mutations/DEV-0026.json` (host de terceiro na redirect URI e na origem). Ao criar o ambiente de produção, as URLs novas entram na lista esperada do teste no mesmo PR.

## resultado-assincrono-nao-grava-depois-do-encerramento

**Regra:** quando uma operação assíncrona (renovar token, buscar dado) pode terminar depois de a sessão
ter sido encerrada, o resultado é conferido contra o estado atual antes de gravar qualquer coisa.
Encerrar a sessão invalida o que estava em andamento (contador de geração ou cancelamento); sem isso,
a resposta atrasada devolve ao aparelho o que o logout acabou de apagar.

### Ocorrências

- **Revisão:** Code Review do PR #196, no SHA `8014d9a` (MEDIUM: renovação em andamento não invalidada pelo logout) — DEV-0028 (#20) · **PR:** #196
  - **Impacto:** com uma renovação pendente, `signOut()` ou `reject()` limpavam a memória e o cofre, mas a resposta do Keycloak regravava o token em memória e o refresh token no cofre. O app ficava "sem sessão" com credencial guardada e voltaria a entrar sozinho na reabertura. Não era alcançável pelas telas daquele commit; seria com as telas do E2.
  - **Correção:** `forget()` incrementa uma geração e descarta a renovação pendente; `renew` confere a geração antes de gravar e lança `InvalidSessionError` se ela mudou. No mesmo PR: falha do cofre ao apagar não impede o encerramento, e o login só abre sessão depois de guardar o refresh token.
  - **Prevenção:** testes de logout, `reject` e retomada com renovação pendente, e mutantes M16, M17 e M18 de `mutations/DEV-0028.json`. Ao escrever qualquer `then` que grave estado de sessão, pergunte o que acontece se a sessão acabar antes de ele rodar.
