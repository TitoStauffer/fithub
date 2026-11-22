#!/usr/bin/env node
// Decide o que o workflow CI precisa rodar a partir dos arquivos alterados (DEV-0016, DEV-0017).
//
//   node tools/repo-checks/ci-changes.mjs <base-sha> <head-sha>              # áreas do PR
//   node tools/repo-checks/ci-changes.mjs --all                             # todas as áreas
//   node tools/repo-checks/ci-changes.mjs --services <base-sha> <head-sha>  # imagens a gerar
//   node tools/repo-checks/ci-changes.mjs --services --all                  # todas as imagens
//
// Escreve no stdout, no formato do $GITHUB_OUTPUT, as linhas `java=true|false`, `apps=...` e
// `infra=...`, ou `services=[...]` (JSON com os serviços que têm Dockerfile). Na dúvida roda tudo:
// lista vazia, mudança no próprio CI ou nestes scripts liga todas as áreas e todas as imagens. Se o
// git falhar, o script falha, e os jobs que dependem dele ficam sem sucesso (o merge bloqueia).
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const AREAS = ['java', 'apps', 'infra'];

// Mudança em qualquer um destes caminhos pode alterar o que o CI verifica: roda tudo.
const CI_CONFIGURATION = [
  (path) => path === '.github/workflows/ci.yml',
  (path) => path.startsWith('tools/repo-checks/'),
];

// Build Java compartilhado por todos os serviços: POM pai, libs, Maven e o contexto das imagens.
// .mvn/ e mvnw* ainda não existem; se vierem (Maven Wrapper, maven.config), mudam o build.
const SHARED_JAVA_BUILD = (path) => path === 'pom.xml' || path === '.java-version' || path === '.dockerignore'
  || path.startsWith('.mvn/') || path.startsWith('mvnw') || path.startsWith('libs/');

const MATCHERS = {
  java: (path) => SHARED_JAVA_BUILD(path) || path.startsWith('services/'),
  apps: (path) => path.startsWith('apps/') || path.startsWith('packages/')
    || ['package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', '.nvmrc', '.npmrc'].includes(path),
  infra: (path) => path.startsWith('infra/'),
};

function normalize(files) {
  return files.map((file) => file.trim()).filter(Boolean);
}

function touchesCiConfiguration(paths) {
  return paths.length === 0 || paths.some((path) => CI_CONFIGURATION.some((matches) => matches(path)));
}

/** Áreas a rodar para a lista de arquivos alterados. */
export function classifyChanges(files) {
  const paths = normalize(files);
  const runEverything = touchesCiConfiguration(paths);
  return Object.fromEntries(AREAS.map((area) => [area, runEverything || paths.some(MATCHERS[area])]));
}

/**
 * Serviços cuja imagem precisa ser gerada: os que tiveram arquivo alterado e, se o build compartilhado
 * (POM pai, libs, Maven, .dockerignore) ou o próprio CI mudou, todos.
 */
export function classifyServices(files, services) {
  const paths = normalize(files);
  const everyService = touchesCiConfiguration(paths) || paths.some(SHARED_JAVA_BUILD);
  return services.filter((service) => everyService || paths.some((path) => path.startsWith(`services/${service}/`)));
}

/** Serviços com imagem: pastas de services/ que têm Dockerfile. Nome fora de [a-z0-9-] é erro. */
export function discoverServices(root = '.') {
  const dir = join(root, 'services');
  const services = readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && existsSync(join(dir, entry.name, 'Dockerfile')))
    .map((entry) => entry.name)
    .sort();
  const invalid = services.find((service) => !/^[a-z0-9-]+$/.test(service));
  if (invalid) throw new Error(`Nome de serviço inválido para imagem: ${invalid}`);
  return services;
}

export function formatOutputs(areas) {
  return AREAS.map((area) => `${area}=${areas[area] ? 'true' : 'false'}`).join('\n');
}

/**
 * Caminhos alterados entre a base e o HEAD. `--no-renames` é obrigatório: com a detecção de renomeação
 * (padrão do git), um arquivo movido de services/ para fora das áreas apareceria só pelo caminho novo,
 * e a área de origem ficaria sem build (falso verde num check exigido). `-z` também: sem ele, o git põe
 * entre aspas e escapa (core.quotePath) caminhos com acento, aspas ou quebra de linha, e
 * `"services/...\303\247..."` não casaria com services/.
 */
export function changedFiles(base, head, { cwd } = {}) {
  for (const sha of [base, head]) {
    if (!/^[0-9a-f]{40}$/i.test(sha ?? '')) throw new Error(`SHA inválido: ${sha ?? '(vazio)'}`);
  }
  return execFileSync('git', ['diff', '--name-only', '--no-renames', '-z', `${base}...${head}`], { encoding: 'utf8', cwd }).split('\0');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  if (args[0] === '--services') {
    const [first, second] = args.slice(1);
    const files = first === '--all' ? [] : changedFiles(first, second);
    const services = classifyServices(files, discoverServices());
    process.stdout.write(`services=${JSON.stringify(services)}\n`);
    console.error(`imagens: ${services.length > 0 ? services.join(', ') : 'nenhuma'}`);
  } else {
    const [first, second] = args;
    const areas = first === '--all' ? classifyChanges([]) : classifyChanges(changedFiles(first, second));
    process.stdout.write(`${formatOutputs(areas)}\n`);
    for (const area of AREAS) console.error(`${area}: ${areas[area] ? 'roda' : 'sem mudança, não roda'}`);
  }
}
