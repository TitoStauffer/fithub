# dev-seed

Dados de exemplo do ambiente local inteiro (DEV-0181). Roda como o serviço `seed` de
`infra/compose/docker-compose.app.yml`, depois que a borda fica saudável, e para. **Só para desenvolvimento:** mexe
só no realm do Keycloak local, nunca em `infra/keycloak/realm-fithub.json`, que também vai para produção. O compose
de produção não tem o seed (`infra/compose/prod.test.mjs`).

## O que ele faz

Pelo admin do Keycloak:

- um client só local, `fithub-dev-seed`, para pedir o token das contas de teste por senha;
- o retorno do Expo Go (`exp://*`) no client `fithub-mobile` e o endereço da borda no client `fithub-web`;
- três contas de teste, com e-mail verificado e a senha de `FITHUB_SEED_PASSWORD` (`compose/.env.example`):
  `personal@fithub.test`, `nutri@fithub.test` e `cliente@fithub.test`.

Pelas APIs, como as pessoas fariam:

1. o cadastro da personal (CREF) e da nutricionista (CRN), que começa o teste grátis;
2. o perfil do cliente e o aceite dos dois vínculos pelo código pessoal, com o consentimento;
3. um exercício próprio da personal e uma ficha de treino publicada;
4. um alimento próprio da nutricionista e um plano alimentar publicado.

Repetir não duplica nada: cada passo confere antes o que já existe, e a senha volta à de teste.

## Rodar de novo

```sh
docker compose -f infra/compose/docker-compose.dev.yml -f infra/compose/docker-compose.app.yml run --rm seed
```
