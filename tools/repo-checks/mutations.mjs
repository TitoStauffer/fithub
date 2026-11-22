#!/usr/bin/env node
// Verificação por mutação com mutantes declarados (DEV-0116). Cada tarefa que cria guarda nova traz
// mutations/<CHAVE>.json; o CI aplica cada mutante, roda o comando de teste declarado e exige que ele
// falhe. Formato do manifesto em docs/00-oficial/ENGENHARIA.md (seção de testes).
//
//   node tools/repo-checks/mutations.mjs --area <java|apps|tools> <manifesto>...
//
// Falha quando: o manifesto é inválido; um trecho não aparece exatamente uma vez no arquivo; um mutante
// sobrevive (o comando termina com sucesso); ou o arquivo não volta byte a byte ao original.
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, isAbsolute, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const AREAS = ['java', 'apps', 'tools'];
// Executáveis que um mutante pode chamar. O comando roda sem shell, com argv explícito.
export const ALLOWED_COMMANDS = new Set(['mvn', 'pnpm', 'node', 'npm']);
const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const COMMAND_TIMEOUT_MS = 20 * 60 * 1000;

/** Problemas do manifesto (lista vazia quando é válido). O nome do arquivo precisa ser a chave. */
export function validateManifest(manifest, fileName, root = ROOT) {
  const problems = [];
  const key = basename(fileName, '.json');
  if (!/^DEV-\d{4}$/.test(key)) problems.push(`nome do manifesto precisa ser mutations/DEV-NNNN.json: ${fileName}`);
  if (manifest?.issue !== key) problems.push(`"issue" precisa ser "${key}"`);
  const mutants = manifest?.mutants;
  if (!Array.isArray(mutants) || mutants.length === 0) {
    problems.push('"mutants" precisa ser uma lista com ao menos um mutante');
    return problems;
  }
  const ids = new Set();
  for (const [index, mutant] of mutants.entries()) {
    const where = `mutante ${mutant?.id ?? `#${index + 1}`}`;
    if (typeof mutant?.id !== 'string' || !/^[A-Za-z0-9_-]+$/.test(mutant.id)) problems.push(`${where}: "id" inválido`);
    else if (ids.has(mutant.id)) problems.push(`${where}: "id" repetido`);
    else ids.add(mutant.id);
    if (typeof mutant?.description !== 'string' || mutant.description.trim() === '') problems.push(`${where}: "description" vazio`);
    if (!AREAS.includes(mutant?.area)) problems.push(`${where}: "area" precisa ser ${AREAS.join(', ')}`);
    if (typeof mutant?.file !== 'string' || !insideRepository(mutant.file, root)) problems.push(`${where}: "file" precisa ser um caminho relativo dentro do repositório`);
    else if (!existsSync(resolve(root, mutant.file))) problems.push(`${where}: arquivo não existe: ${mutant.file}`);
    if (typeof mutant?.find !== 'string' || mutant.find === '') problems.push(`${where}: "find" vazio`);
    if (typeof mutant?.replace !== 'string' || mutant.replace === mutant?.find) problems.push(`${where}: "replace" precisa ser texto diferente de "find"`);
    const command = mutant?.command;
    if (!Array.isArray(command) || command.length === 0 || command.some((part) => typeof part !== 'string' || part === '')) {
      problems.push(`${where}: "command" precisa ser uma lista de argumentos`);
    } else if (!ALLOWED_COMMANDS.has(command[0])) {
      problems.push(`${where}: comando "${command[0]}" fora da lista (${[...ALLOWED_COMMANDS].join(', ')})`);
    }
  }
  return problems;
}

function insideRepository(path, root) {
  if (isAbsolute(path) || path.includes('\\')) return false;
  const target = normalize(resolve(root, path));
  return target.startsWith(normalize(root).replace(/[\\/]$/, '') + sep) && !path.split('/').includes('..');
}

function sha256(content) {
  return createHash('sha256').update(content).digest('hex');
}

function occurrences(text, part) {
  let count = 0;
  for (let index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + 1)) count += 1;
  return count;
}

/** Roda o comando com argv, sem shell; no Windows, mvn/pnpm/npm são .cmd e precisam do cmd. */
function defaultRun(command, root) {
  const windowsShim = process.platform === 'win32' && command[0] !== 'node';
  const result = windowsShim
    ? spawnSync('cmd.exe', ['/d', '/s', '/c', ...command], { cwd: root, stdio: 'inherit', timeout: COMMAND_TIMEOUT_MS })
    : spawnSync(command[0], command.slice(1), { cwd: root, stdio: 'inherit', timeout: COMMAND_TIMEOUT_MS });
  return result.status;
}

/**
 * Aplica um mutante: troca o trecho único, roda o comando e restaura. Devolve o resultado; o arquivo é
 * restaurado mesmo se o comando lançar, e a restauração é conferida pelo hash.
 */
export function applyMutant(mutant, { root = ROOT, run = defaultRun, write = writeFileSync } = {}) {
  const path = resolve(root, mutant.file);
  const original = readFileSync(path);
  const text = original.toString('utf8');
  // O trecho pode estar com LF no manifesto e CRLF no arquivo (checkout no Windows).
  const eol = text.includes('\r\n') ? '\r\n' : '\n';
  const find = mutant.find.replace(/\r?\n/g, eol);
  const replace = mutant.replace.replace(/\r?\n/g, eol);
  const count = occurrences(text, find);
  if (count !== 1) return { id: mutant.id, outcome: 'ANCHOR_NOT_UNIQUE', detail: `"find" aparece ${count} vez(es) em ${mutant.file}` };
  let status;
  try {
    write(path, text.replace(find, () => replace));
    status = run(mutant.command, root);
  } finally {
    write(path, original);
  }
  if (sha256(readFileSync(path)) !== sha256(original)) {
    return { id: mutant.id, outcome: 'NOT_RESTORED', detail: `${mutant.file} não voltou ao original` };
  }
  if (status === 0) return { id: mutant.id, outcome: 'SURVIVED', detail: `o comando passou com o mutante: ${mutant.command.join(' ')}` };
  return { id: mutant.id, outcome: 'KILLED', detail: status === null ? 'comando interrompido (timeout ou sinal)' : `comando saiu com ${status}` };
}

/** Valida e aplica os mutantes da área em cada manifesto. Devolve os resultados e os erros. */
export function runManifests(files, area, options = {}) {
  const root = options.root ?? ROOT;
  const results = [];
  const errors = [];
  for (const file of files) {
    let manifest;
    try {
      manifest = JSON.parse(readFileSync(resolve(root, file), 'utf8'));
    } catch (error) {
      errors.push(`${file}: JSON inválido (${error.message})`);
      continue;
    }
    const problems = validateManifest(manifest, file, root);
    if (problems.length > 0) {
      errors.push(...problems.map((problem) => `${file}: ${problem}`));
      continue;
    }
    for (const mutant of manifest.mutants.filter((candidate) => candidate.area === area)) {
      const result = applyMutant(mutant, { root, run: options.run, write: options.write });
      results.push({ manifest: file, ...result });
      if (result.outcome !== 'KILLED') errors.push(`${file} ${result.id}: ${result.outcome} — ${result.detail}`);
    }
  }
  return { results, errors };
}

/** Áreas citadas pelos manifestos (para o CI ligar o job certo quando só o manifesto muda). */
export function manifestAreas(files, root = ROOT) {
  const areas = new Set();
  for (const file of files) {
    try {
      const manifest = JSON.parse(readFileSync(resolve(root, file), 'utf8'));
      for (const mutant of manifest?.mutants ?? []) if (AREAS.includes(mutant?.area)) areas.add(mutant.area);
    } catch {
      // Manifesto ilegível liga todas as áreas: o job da área reporta o erro.
      AREAS.forEach((area) => areas.add(area));
    }
  }
  return [...areas].sort();
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const areaIndex = args.indexOf('--area');
  const area = areaIndex >= 0 ? args[areaIndex + 1] : undefined;
  const files = args.filter((_, index) => index !== areaIndex && index !== areaIndex + 1);
  if (!AREAS.includes(area)) {
    console.error(`Informe --area ${AREAS.join('|')} e os manifestos.`);
    process.exit(2);
  }
  if (files.length === 0) {
    console.log('Nenhum manifesto de mutação alterado neste PR.');
    process.exit(0);
  }
  const { results, errors } = runManifests(files, area);
  for (const result of results) console.log(`${result.manifest} ${result.id}: ${result.outcome} (${result.detail})`);
  if (results.length === 0 && errors.length === 0) console.log(`Nenhum mutante da área ${area} nos manifestos alterados.`);
  for (const error of errors) console.error(`ERRO: ${error}`);
  process.exit(errors.length > 0 ? 1 : 0);
}
