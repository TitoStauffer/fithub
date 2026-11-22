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
