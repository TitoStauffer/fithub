# Aprendizados

Problemas confirmados em Code Review e o que fazemos para não repeti-los.
Regras de registro: [AGENTS.md](../../../AGENTS.md), seção **Aprendizados**.

Organize por tema, um arquivo por tema (ex.: `autorizacao.md`,
`mensageria.md`, `migrations.md`). Cada regra segue o formato:

```markdown
## <identificador-do-padrao>

**Regra:** o que fazer (ou evitar).

### Ocorrências

- **Revisão:** <link do comentário ou revisão no PR> · **SHA:** <sha>
  - **Impacto:** …
  - **Correção:** …
  - **Prevenção:** teste, regra de ArchUnit, checklist…
```

Ocorrência repetida entra como nova linha em **Ocorrências** da regra
existente. Nunca registre segredos, dados pessoais ou valores reais de
ambiente.
