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

## reparo-pos-restauracao-roda-so-com-marcador

**Regra:** um passo que reaplica uma exclusão (ou qualquer reparo) depois de restaurar um backup roda só quando a
restauração aconteceu, com um marcador gravado pelo procedimento de restauração e apagado no fim do reparo. Rodar
sempre, por id, apaga também o dado que o titular criou depois da exclusão. O marcador não pode depender de uma
tabela que o backup restaurado talvez não tenha: a restauração cria a tabela se faltar, e a migration dela usa
`if not exists`.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #316, no SHA `de84ecf` (MEDIUM) — DEV-0172 (#313) · **PR:** #316
  - **Impacto:** a reaplicação incondicional a cada 6 h apagaria o perfil de cliente recriado por quem também é
    profissional, e o vínculo aceito de novo, sem `VinculoEncerrado`.
  - **Correção:** a `restore.sh` grava uma linha em `restore_pending`, e os serviços reaplicam só com ela e a
    apagam no fim.
  - **Prevenção:** esta regra, o teste `semRestauracaoODadoDeUmIdDaListaFica` nos três serviços e os mutantes do
    marcador.
- **Revisão:** Code Review CHANGES_REQUESTED do PR #316, no SHA `d920891` (MEDIUM) — DEV-0172 (#313) · **PR:** #316
  - **Impacto:** a `restore.sh` só gravava o marcador quando a tabela existia no banco restaurado. Num backup de
    antes da migration do marcador, o dado de saúde de quem excluiu a conta depois do backup voltaria sem
    reaplicação.
  - **Correção:** a `restore.sh` cria `restore_pending` como dono do banco antes de gravar, e as migrations usam
    `create table if not exists`.
  - **Prevenção:** o `backup.test.sh` restaura um backup sem a tabela e confere o marcador e o dono dela.

## credencial-de-producao-isolada-por-servico

**Regra:** em produção, cada serviço recebe só as credenciais do que é dele (banco, broker, storage): uma senha por usuário de banco, nunca
uma compartilhada, e nenhuma credencial de outro serviço ou do Keycloak no container. O isolamento por banco
(ADR-001) só vale se a senha também for separada. Uma guarda confere quem recebe cada senha no compose resolvido.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #322, no SHA `e4af180` (MEDIUM) — DEV-0110 (#102) · **PR:** #322
  - **Impacto:** com uma senha só para os usuários `account`, `client`, `training`, `nutrition` e `keycloak`, quem
    comprometesse um serviço lia o dado de saúde do client e a base do Keycloak, inclusive o secret que atribui papel.
  - **Correção:** uma variável de senha por usuário, lida pelo `init-databases.sh` e passada só ao serviço dono.
  - **Prevenção:** esta regra, o teste `cada usuário de banco tem senha própria, e só o dono do banco a recebe` e o
    mutante M9 de `mutations/DEV-0110.json`.
- **Revisão:** Code Review CHANGES_REQUESTED do PR #322, no SHA `8fe606b` (2 MEDIUM) — DEV-0110 (#102) · **PR:** #322
  - **Impacto:** a mesma falha, no broker e no storage.
    - Os quatro serviços usavam o administrador do RabbitMQ. Um serviço comprometido publicaria um `VinculoAtivado`
      forjado na exchange do client e ganharia acesso a dados de outro.
    - O client e o nutrition usavam a mesma identidade S3. Cada um leria as fotos do outro, inclusive as corporais,
      sem a verificação de vínculo.
  - **Correção:**
    - um usuário do RabbitMQ por serviço (`rabbitmq/init-users.sh`), que só publica na própria exchange e só lê as
      próprias filas; o administrador fica só para a operação;
    - uma identidade S3 por serviço, só com o próprio bucket (`seaweedfs/start.sh`).
  - **Prevenção:** a regra passa a valer para toda credencial, não só para a de banco. O teste
    `cada credencial chega só ao dono e a quem a cria` cobre todos os segredos do `.env`. A fumaça confere que a
    publicação forjada e o acesso ao bucket alheio são recusados. Mutantes M11 a M14.

## acesso-administrativo-testado-pelo-caminho-documentado

**Regra:** quando a borda esconde uma interface de administração, o caminho de acesso documentado (túnel SSH, porta
local) é testado de verdade: a interface tem que abrir por ele, sem redirecionar para o endereço público que a borda
bloqueia.

### Ocorrências

- **Revisão:** Code Review CHANGES_REQUESTED do PR #322, no SHA `e4af180` (MEDIUM) — DEV-0110 (#102) · **PR:** #322
  - **Impacto:** sem `KC_HOSTNAME_ADMIN`, o console do Keycloak aberto pelo túnel mandava o login para o domínio
    público, onde a borda responde 404, e o operador ficava sem administração.
  - **Correção:** `KC_HOSTNAME_ADMIN` com o endereço do túnel.
  - **Prevenção:** esta regra, a checagem do console pelo `127.0.0.1` no `prod-smoke.test.sh` e o mutante M10.
