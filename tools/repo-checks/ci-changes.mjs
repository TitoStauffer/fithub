#!/usr/bin/env node
// Decide quais áreas do workflow CI precisam rodar a partir dos arquivos alterados (DEV-0016).
//
//   node tools/repo-checks/ci-changes.mjs <base-sha> <head-sha>   # PR: só o que mudou
//   node tools/repo-checks/ci-changes.mjs --all                  # push na master: tudo
//
// Escreve no stdout as linhas `java=true|false`, `apps=...` e `infra=...`, no formato do $GITHUB_OUTPUT.
// Na dúvida roda tudo: lista vazia, mudança no próprio CI ou nestes scripts liga todas as áreas. Se o
// git falhar, o script falha, e os jobs que dependem dele ficam sem sucesso (o merge bloqueia).
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const AREAS = ['java', 'apps', 'infra'];

// Mudança em qualquer um destes caminhos pode alterar o que o CI verifica: roda tudo.
const CI_CONFIGURATION = [
  (path) => path === '.github/workflows/ci.yml',
  (path) => path.startsWith('tools/repo-checks/'),
];

const MATCHERS = {
  java: (path) => path === 'pom.xml' || path === '.java-version'
    || path.startsWith('libs/') || path.startsWith('services/'),
  apps: (path) => path.startsWith('apps/') || path.startsWith('packages/')
    || ['package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', '.nvmrc', '.npmrc'].includes(path),
  infra: (path) => path.startsWith('infra/'),
};

/** Áreas a rodar para a lista de arquivos alterados. */
export function classifyChanges(files) {
  const paths = files.map((file) => file.trim()).filter(Boolean);
  const runEverything = paths.length === 0 || paths.some((path) => CI_CONFIGURATION.some((matches) => matches(path)));
  return Object.fromEntries(AREAS.map((area) => [area, runEverything || paths.some(MATCHERS[area])]));
}

export function formatOutputs(areas) {
  return AREAS.map((area) => `${area}=${areas[area] ? 'true' : 'false'}`).join('\n');
}

function changedFiles(base, head) {
  for (const sha of [base, head]) {
    if (!/^[0-9a-f]{40}$/i.test(sha ?? '')) throw new Error(`SHA inválido: ${sha ?? '(vazio)'}`);
  }
  return execFileSync('git', ['diff', '--name-only', `${base}...${head}`], { encoding: 'utf8' }).split('\n');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [first, second] = process.argv.slice(2);
  const areas = first === '--all' ? classifyChanges([]) : classifyChanges(changedFiles(first, second));
  process.stdout.write(`${formatOutputs(areas)}\n`);
  for (const area of AREAS) console.error(`${area}: ${areas[area] ? 'roda' : 'sem mudança, não roda'}`);
}
