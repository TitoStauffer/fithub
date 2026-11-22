#!/bin/sh
# Teste do hook pre-push (DEV-0130). Roda no CI (job GitHub Reviewer) e localmente:
#   sh .githooks/test-pre-push.sh
set -u
hook="$(dirname "$0")/pre-push"
zero=0000000000000000000000000000000000000000
sha=1111111111111111111111111111111111111111
failures=0

check() { # descrição, código esperado, linhas de entrada
  description=$1; expected=$2; input=$3
  printf '%s\n' "$input" | sh "$hook" origin https://example.test/repo.git >/dev/null 2>&1
  actual=$?
  if [ "$actual" -eq "$expected" ]; then echo "ok   - $description"
  else echo "FAIL - $description (esperado $expected, obtido $actual)"; failures=$((failures + 1)); fi
}

# O hook só roda se for executável no índice (100755); senão o git o ignora fora do Windows.
mode=$(git ls-files -s "$hook" 2>/dev/null | cut -d' ' -f1)
if [ "$mode" = "100755" ]; then echo "ok   - o hook está com permissão de execução no índice (100755)"
else echo "FAIL - o hook está sem permissão de execução no índice (modo: ${mode:-ausente})"; failures=$((failures + 1)); fi

check 'bloqueia push para master' 1 "refs/heads/master $sha refs/heads/master $zero"
check 'bloqueia push para main' 1 "refs/heads/main $sha refs/heads/main $zero"
check 'bloqueia push de outra branch local para a master remota' 1 "refs/heads/feature/DEV-0001-x $sha refs/heads/master $zero"
check 'bloqueia quando uma das refs do push é a master' 1 "refs/heads/chore/DEV-0002-y $sha refs/heads/chore/DEV-0002-y $zero
refs/heads/master $sha refs/heads/master $zero"
check 'permite push de branch de tarefa' 0 "refs/heads/chore/DEV-0130-hook $sha refs/heads/chore/DEV-0130-hook $zero"
check 'permite branch cujo nome só contém master' 0 "refs/heads/fix/DEV-0003-master-docs $sha refs/heads/fix/DEV-0003-master-docs $zero"
check 'permite push sem refs' 0 ""

[ "$failures" -eq 0 ] || { echo "$failures verificação(ões) do pre-push falharam."; exit 1; }
