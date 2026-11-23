# account-service

Dono de: profissional (tipo, CREF/CRN), organização e assinatura.

Esqueleto da DEV-0015 (estrutura, execução e testes em [services/README.md](../README.md)). Responsabilidades e eventos: [ENGENHARIA.md §2–3](../../docs/00-oficial/ENGENHARIA.md).

## Cadastro do profissional (DEV-0029)

| Rota | Quem | Resposta |
|---|---|---|
| `POST /professionals/me` | Qualquer conta autenticada | 201 cria; 200 se o mesmo cadastro já existia; 409 se a conta já tem cadastro diferente; 400 com o campo inválido |
| `GET /professionals/me` | Qualquer conta autenticada | 200 com o cadastro da própria conta; 404 se não houver |

- A conta é sempre o `sub` do token. Não existe leitura do cadastro de outra pessoa.
- Corpo do `POST`: `type` (`PERSONAL` ou `NUTRITIONIST`), `registrationNumber` (1 a 6 dígitos),
  `registrationState` (UF) e, só no CREF, `registrationCategory` (`G` ou `P`).
- O nome do profissional vem do perfil do Keycloak (claim `name`, ou nome e sobrenome); sem ele, 400 com o
  campo `name`.
- O cadastro grava o evento `ProfissionalCadastrado` (`account.professional-registered.v1`) no outbox, na
  mesma transação, só na primeira vez ([libs/event-contracts](../../libs/event-contracts/README.md)).
- Depois do cadastro, o serviço atribui o papel no Keycloak ([ADR-008](../../docs/00-oficial/adr/ADR-008-atribuicao-de-papel-pelo-account-service.md)).
  Enquanto `roleStatus` for `PENDING`, o token ainda não traz o papel: o front renova a sessão.
- Configuração: `fithub.keycloak-admin.*` (URL do Keycloak e client) e o secret em
  `FITHUB_ACCOUNT_SERVICE_SECRET`; no compose local o padrão já casa com o realm. A retentativa roda a
  cada `fithub.professional.role-retry.interval` (30 s) e desliga com
  `fithub.professional.role-retry.enabled=false`.
