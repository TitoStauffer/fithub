# Aprendizados

Problemas confirmados em Code Review e bugs reais de validação ou produção, e o
que fazemos para não repeti-los.

| Tema | Arquivo |
|---|---|
| Workflows, runner e bots | [automacao-github.md](automacao-github.md) |
| Outbox, consumidores e eventos | [mensageria.md](mensageria.md) |
| Keycloak, realm, clients e login | [identidade.md](identidade.md) |
| Migrations, CHECKs e unicidade | [migrations.md](migrations.md) |
| Contratos de API (OpenAPI) | [contratos-api.md](contratos-api.md) |
| Arquivos no bucket (fotos, URL pré-assinada) | [armazenamento.md](armazenamento.md) |
| Testes de integração (Testcontainers, contexto do Spring) | [testes-de-integracao.md](testes-de-integracao.md) |
| Idempotência e posse de registros (id do cliente, upsert) | [idempotencia-e-posse.md](idempotencia-e-posse.md) |
| Métricas sobre períodos (aderência, janelas de vigência) | [metricas-e-periodos.md](metricas-e-periodos.md) |
| Política de privacidade e termos (o que listar) | [documentacao-legal.md](documentacao-legal.md) |
| Auditoria de acesso a dados sensíveis | [auditoria.md](auditoria.md) |

Regras de registro: [AGENTS.md](../../../AGENTS.md), seção **Aprendizados**.

Organize por tema, um arquivo por tema (ex.: `autorizacao.md`,
`mensageria.md`, `migrations.md`). Cada regra segue o formato:

```markdown
## <identificador-do-padrao>

**Regra:** o que fazer (ou evitar).

### Ocorrências

- **Revisão:** <link da revisão no PR, ou a origem do bug de validação/produção (issue, execução)> · **PR:** <PR da correção> · **SHA:** <sha, se já integrado>
  - **Impacto:** …
  - **Correção:** …
  - **Prevenção:** teste, regra de ArchUnit, checklist…
```

Registre na branch da correção, antes de pedir nova revisão: o PR identifica a
correção e o SHA é opcional enquanto ela não está integrada.

Ocorrência repetida entra como nova linha em **Ocorrências** da regra
existente. Nunca registre segredos, dados pessoais ou valores reais de
ambiente.
