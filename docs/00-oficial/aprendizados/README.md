# Aprendizados

Problemas confirmados em Code Review e bugs reais de validação ou produção, e o
que fazemos para não repeti-los.

| Tema | Arquivo |
|---|---|
| Workflows, runner e bots | [automacao-github.md](automacao-github.md) |

Regras de registro: [AGENTS.md](../../../AGENTS.md), seção **Aprendizados**.

Organize por tema, um arquivo por tema (ex.: `autorizacao.md`,
`mensageria.md`, `migrations.md`). Cada regra segue o formato:

```markdown
## <identificador-do-padrao>

**Regra:** o que fazer (ou evitar).

### Ocorrências

- **Revisão:** <link da revisão no PR, ou a origem do bug de validação/produção (issue, execução)> · **PR:** <PR da correção> · **SHA:** <sha da correção>
  - **Impacto:** …
  - **Correção:** …
  - **Prevenção:** teste, regra de ArchUnit, checklist…
```

Ocorrência repetida entra como nova linha em **Ocorrências** da regra
existente. Nunca registre segredos, dados pessoais ou valores reais de
ambiente.
