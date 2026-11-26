# Aprendizados — operação (backup e restauração)

Scripts e procedimentos que mexem nos dados de produção (`infra/backup/`).

## estado-salvo-sobrevive-a-falha

**Regra:** num procedimento que apaga e regrava dados, o que é salvo antes para ser regravado depois fica num lugar
durável e só sai depois do sucesso. Nada de pasta temporária apagada por `trap ... EXIT`. Regrave o mais cedo possível,
logo depois de cada passo, e não só no fim. Toda leitura que decide o que salvar falha de forma explícita: no shell,
dentro de um `if` o `set -e` não vale, e a falha do comando vira "falso" em silêncio.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #314, no SHA `d55c5d3` (HIGH e MEDIUM) — DEV-0108 (#100) · **PR:** #314
  - **Impacto:** a restauração guardava a lista `deleted_account` atual numa pasta temporária apagada na saída, e só a
    regravava depois de restaurar todos os bancos. Qualquer falha no meio apagava a lista, e quem excluiu a conta
    depois do backup sumia dela: o dado sensível voltava sem como reaplicar a exclusão. Uma falha ao ler a lista
    virava "banco sem lista", em silêncio.
  - **Correção:** a cópia fica em `BACKUP_DIR/restore-<backup>-<agora>/` e só sai com tudo restaurado. A lista volta
    logo depois de cada banco, e a leitura aborta de forma explícita.
  - **Prevenção:** esta regra e o caso de dump corrompido no `backup.test.sh`. A prova manual dos mutantes, que
    regravam só no fim e apagam a cópia na falha, está no PR.
