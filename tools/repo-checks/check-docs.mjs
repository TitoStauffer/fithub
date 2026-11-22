#!/usr/bin/env node
// Verificações da árvore de documentação (DEV-0010), rodadas no CI:
//  1. nenhum caminho versionado passa de MAX_PATH_LENGTH caracteres (checkout no Windows);
//  2. links relativos dos .md versionados (fora de legacy/) apontam para arquivos que existem;
//  3. o docs/HISTORICO.md cita um commit que contém cada caminho removido listado, e esses
//     caminhos não existem mais no HEAD.
// Uso: node tools/repo-checks/check-docs.mjs [raiz-do-repositório]
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, normalize, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MAX_PATH_LENGTH = 200;
export const HISTORY_FILE = 'docs/HISTORICO.md';

function git(root, args) {
  return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
}

export function trackedFiles(root) {
  return git(root, ['ls-files', '-z']).split('\0').filter(Boolean);
}

export function checkPathLength(files, max = MAX_PATH_LENGTH) {
  const longest = files.reduce((current, path) => (path.length > current.length ? path : current), '');
  const errors = files.filter((path) => path.length > max).map((path) => `caminho com ${path.length} caracteres (máximo ${max}): ${path}`);
  return { errors, longest: longest.length };
}

// Links Markdown inline: [texto](destino). Ignora URLs, âncoras e e-mails.
const LINK = /\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;

export function checkRelativeLinks(root, files) {
  const tracked = new Set(files);
  const trackedDirs = new Set(files.flatMap((path) => path.split('/').slice(0, -1).map((_, index, parts) => parts.slice(0, index + 1).join('/'))));
  const errors = [];
  for (const file of files.filter((path) => path.endsWith('.md') && !path.startsWith('legacy/'))) {
    const text = readFileSync(join(root, file), 'utf8').replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
    for (const [, rawTarget] of text.matchAll(LINK)) {
      if (/^([a-z][a-z0-9+.-]*:|#)/i.test(rawTarget)) continue;
      const target = decodeURIComponent(rawTarget.split('#')[0]);
      if (!target) continue;
      const resolved = normalize(join(dirname(file), target)).replace(/\\/g, '/').replace(/\/$/, '');
      if (resolved.startsWith('..')) continue; // fora do repositório (ex.: links do GitHub relativos ao repo)
      if (!tracked.has(resolved) && !trackedDirs.has(resolved)) errors.push(`${file}: link para caminho inexistente: ${rawTarget}`);
    }
  }
  return { errors };
}

export function checkHistory(root, files) {
  if (!files.includes(HISTORY_FILE)) return { errors: [], checked: 0 };
  const text = readFileSync(join(root, HISTORY_FILE), 'utf8');
  const sha = text.match(/\*\*`([0-9a-f]{7,40})`\*\*/)?.[1];
  if (!sha) return { errors: [`${HISTORY_FILE}: não cita o commit de recuperação em **\`sha\`**.`], checked: 0 };
  const section = text.split(/^## /m).find((part) => part.startsWith('O que foi removido')) ?? '';
  const paths = section.split('\n')
    .filter((line) => line.startsWith('| `'))
    .flatMap((line) => [...line.split('|')[1].matchAll(/`([^`]+)`/g)].map(([, path]) => path.replace(/\/$/, '')));
  const errors = [];
  if (paths.length === 0) errors.push(`${HISTORY_FILE}: a tabela "O que foi removido" não lista caminhos.`);
  for (const path of paths) {
    try {
      git(root, ['cat-file', '-e', `${sha}:${path}`]);
    } catch {
      errors.push(`${HISTORY_FILE}: ${path} não existe no commit ${sha}.`);
    }
    if (files.some((tracked) => tracked === path || tracked.startsWith(`${path}/`))) errors.push(`${HISTORY_FILE}: ${path} ainda existe no HEAD.`);
  }
  return { errors, checked: paths.length, sha };
}

export function runChecks(root) {
  const files = trackedFiles(root);
  const length = checkPathLength(files);
  const links = checkRelativeLinks(root, files);
  const history = checkHistory(root, files);
  return { errors: [...length.errors, ...links.errors, ...history.errors], longest: length.longest, files: files.length, history };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = resolve(process.argv[2] ?? '.');
  if (!existsSync(join(root, '.git'))) {
    console.error(`Não é a raiz de um repositório git: ${relative(process.cwd(), root) || '.'}`);
    process.exit(2);
  }
  const result = runChecks(root);
  console.log(`Arquivos versionados: ${result.files}; maior caminho: ${result.longest} caracteres (máximo ${MAX_PATH_LENGTH}).`);
  if (result.history.sha) console.log(`${HISTORY_FILE}: ${result.history.checked} caminho(s) removido(s) conferido(s) no commit ${result.history.sha}.`);
  if (result.errors.length) {
    for (const error of result.errors) console.error(`ERRO: ${error}`);
    process.exit(1);
  }
  console.log('Documentação do repositório OK: caminhos, links relativos e histórico.');
}
