# Métricas e períodos

Métricas calculadas na leitura sobre um período (aderência, evolução, séries), em dias de America/Sao_Paulo.

## janela-de-vigencia-sem-sobreposicao

**Regra:** quando uma coisa substitui outra num dia (plano, ficha, versão), as duas janelas de vigência não podem
contar juntas nesse dia. Com as pontas incluídas dos dois lados, o dia da troca soma o substituído e o novo, e a métrica
dobra o esperado. Decida um dono por dia (o que fica valendo no fim do dia) e limite o feito ao esperado. O teste
precisa ter as duas janelas tocando no mesmo dia, com datas realistas: o substituto começa no dia em que o outro sai.

### Ocorrências

- **Revisão:** Code Review do PR #277, no SHA `cd4951a` (MEDIUM: no dia em que um plano alimentar substitui outro,
  as refeições prescritas dos dois eram somadas) — DEV-0092 (#84) · **PR:** #277
  - **Impacto:** aderência à dieta subestimada no dia de cada troca de plano, para a nutricionista e para o paciente
    (FR-57, FR-58).
  - **Correção:** `DietAdherence` passou a prescrever só o plano mais novo em vigor no dia; os registros do dia nos dois
    planos contam, limitados ao prescrito.
  - **Prevenção:** teste de domínio com a troca no mesmo dia, em ordem direta e invertida, e com registros acima do
    prescrito; teste de API com o substituto publicado no dia da substituição (antes, as duas publicações tinham sido
    recuadas para a mesma data, e o teste fixava a soma); mutantes M21 a M23 de `mutations/DEV-0092.json`. A
    aderência do treino (DEV-0091) soma fichas por semana, na proporção dos dias, e tem o mesmo risco no dia da troca:
    vale conferir quando a fórmula dela for revista.
