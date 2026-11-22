#!/usr/bin/env node
// Guarda contra teste pulado (DEV-0016, AGENTS.md): todo teste pulado precisa de justificativa com a
// issue que o reativa, como em @Disabled("motivo + DEV-xxxx"). Lê os relatórios JUnit XML.
//
//   node tools/repo-checks/check-skipped-tests.mjs [--allow-none] <dir>...
//
// Procura relatórios em pastas surefire-reports/failsafe-reports e em arquivos junit*.xml, sem entrar
// em node_modules. Sem nenhum relatório, falha (teste que não rodou também é teste pulado), a menos
// que --allow-none seja passado.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const JUSTIFICATION = /DEV-\d{4}/;
const REPORT_DIRS = new Set(['surefire-reports', 'failsafe-reports']);

function decode(value) {
  return value.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

function attribute(attributes, name) {
  const match = new RegExp(`\\b${name}="([^"]*)"`).exec(attributes);
  return match ? decode(match[1]) : undefined;
}

/** Testes de um relatório JUnit XML: total e os pulados sem a issue na justificativa. */
export function inspectReport(xml) {
  let total = 0;
  const unjustified = [];
  for (const match of xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
    total += 1;
    const skipped = /<skipped\b([^>]*?)\/?>/.exec(match[2] ?? '');
    if (!skipped) continue;
    const reason = attribute(skipped[1], 'message') ?? '';
    if (!JUSTIFICATION.test(reason)) {
      unjustified.push({
        test: `${attribute(match[1], 'classname') ?? '?'}.${attribute(match[1], 'name') ?? '?'}`,
        reason: reason || '(sem motivo)',
      });
    }
  }
  return { total, unjustified };
}

export function findReports(root) {
  const reports = [];
  const visit = (dir, insideReportDir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      const path = join(dir, entry.name);
      if (entry.isDirectory()) visit(path, insideReportDir || REPORT_DIRS.has(entry.name));
      else if (entry.name.endsWith('.xml') && (insideReportDir || /^junit.*\.xml$/i.test(entry.name))) reports.push(path);
    }
  };
  if (statSync(root, { throwIfNoEntry: false })?.isDirectory()) visit(root, false);
  return reports;
}

export function checkSkippedTests(roots, { allowNone = false } = {}) {
  const reports = roots.flatMap(findReports);
  let total = 0;
  const unjustified = [];
  for (const report of reports) {
    const result = inspectReport(readFileSync(report, 'utf8'));
    total += result.total;
    unjustified.push(...result.unjustified);
  }
  const errors = [];
  if (total === 0 && !allowNone) errors.push(`Nenhum teste encontrado em relatórios JUnit sob: ${roots.join(', ')}.`);
  for (const { test, reason } of unjustified) errors.push(`Teste pulado sem issue na justificativa: ${test} — ${reason}`);
  return { reports: reports.length, total, errors };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const allowNone = args.includes('--allow-none');
  const roots = args.filter((arg) => arg !== '--allow-none');
  if (roots.length === 0) {
    console.error('Informe ao menos uma pasta com relatórios JUnit.');
    process.exit(2);
  }
  const { reports, total, errors } = checkSkippedTests(roots, { allowNone });
  for (const error of errors) console.error(`ERRO: ${error}`);
  if (errors.length > 0) {
    console.error('Teste desabilitado precisa de @Disabled("motivo + DEV-xxxx") (AGENTS.md).');
    process.exit(1);
  }
  console.log(`Testes pulados conferidos: ${total} teste(s) em ${reports} relatório(s), nenhum pulado sem justificativa.`);
}
