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

## arquivo-com-dado-sensivel-nasce-restrito

**Regra:** script que grava dump, cópia ou exportação com dado pessoal ou de saúde define `umask 077` no início (arquivos
600, pastas 700), e o teste confere as permissões. Container que roda como root com o umask padrão (022) deixa tudo
legível para qualquer usuário do host. Cada guarda do script (validação de entrada, recusa, aborto) tem um caso no teste
que só ela derruba, provado por mutação.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #314, no SHA `0deec40` (MEDIUM e MEDIUM) — DEV-0108 (#100) · **PR:** #314
  - **Impacto:** os dumps, com perfil de saúde, avaliações e restrições, e a cópia da lista de ids excluídos saíam 0644
    e 0755 no volume da VPS (NFR-02). Cinco guardas novas da restauração não tinham teste.
  - **Correção:** `umask 077` nos dois scripts e um caso no `backup.test.sh` para cada guarda.
  - **Prevenção:** esta regra, a checagem de permissões no teste e a prova manual de 17 mutantes no PR.
