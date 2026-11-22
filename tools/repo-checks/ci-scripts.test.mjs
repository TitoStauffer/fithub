import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { execFileSync } from 'node:child_process';

import { changedFiles, classifyChanges, formatOutputs } from './ci-changes.mjs';
import { checkSkippedTests, inspectReport } from './check-skipped-tests.mjs';

// --- ci-changes ---

test('cada área roda só quando um caminho dela muda', () => {
  assert.deepEqual(classifyChanges(['services/client-service/src/main/java/A.java']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['libs/java-commons/pom.xml']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['pom.xml']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['apps/web/src/App.tsx']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['pnpm-lock.yaml']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['infra/compose/docker-compose.dev.yml']), { java: false, apps: false, infra: true });
});

test('documentação e ferramentas de revisão não ligam serviços, apps nem infraestrutura', () => {
  assert.deepEqual(classifyChanges(['docs/00-oficial/PRD.md', 'tools/github-reviewer/src/cli.mjs', 'AGENTS.md']),
    { java: false, apps: false, infra: false });
});

test('mudança no próprio CI, nestes scripts ou lista vazia roda tudo', () => {
  const all = { java: true, apps: true, infra: true };
  assert.deepEqual(classifyChanges(['.github/workflows/ci.yml']), all);
  assert.deepEqual(classifyChanges(['tools/repo-checks/ci-changes.mjs']), all);
  assert.deepEqual(classifyChanges([]), all);
  assert.deepEqual(classifyChanges(['', '  ']), all);
});

// Code Review do PR #179: com a detecção de renomeação do git, mover um arquivo de services/ para fora
// das áreas mostrava só o caminho novo, e o Java Services passava sem compilar o serviço que o perdeu.
test('arquivo movido para fora da área ainda liga a área de origem', () => {
  const repo = mkdtempSync(join(tmpdir(), 'ci-changes-'));
  const git = (...args) => execFileSync('git', ['-c', 'user.name=ci', '-c', 'user.email=ci@example.invalid', ...args], { cwd: repo, encoding: 'utf8' }).trim();
  try {
    git('init', '-q');
    mkdirSync(join(repo, 'services', 'a'), { recursive: true });
    writeFileSync(join(repo, 'services', 'a', 'Foo.java'), 'class Foo {\n  int a = 1;\n  int b = 2;\n}\n');
    git('add', '-A');
    git('commit', '-q', '-m', 'base');
    const base = git('rev-parse', 'HEAD');
    mkdirSync(join(repo, 'legacy'));
    git('mv', 'services/a/Foo.java', 'legacy/Foo.java');
    git('commit', '-q', '-m', 'move');
    const head = git('rev-parse', 'HEAD');

    const files = changedFiles(base, head, { cwd: repo }).filter(Boolean).sort();
    assert.deepEqual(files, ['legacy/Foo.java', 'services/a/Foo.java']);
    assert.equal(classifyChanges(files).java, true);
  } finally {
    rmSync(repo, { recursive: true, force: true });
  }
});

test('SHA fora do formato é recusado antes de chamar o git', () => {
  assert.throws(() => changedFiles('main', 'a'.repeat(40)), /SHA inválido/);
  assert.throws(() => changedFiles('a'.repeat(40), '--output=x'), /SHA inválido/);
});

test('as saídas seguem o formato do GITHUB_OUTPUT', () => {
  assert.equal(formatOutputs({ java: true, apps: false, infra: false }), 'java=true\napps=false\ninfra=false');
});

// --- check-skipped-tests ---

const report = (cases) => `<?xml version="1.0"?><testsuite name="s">${cases}</testsuite>`;

test('teste pulado sem a issue na justificativa é recusado', () => {
  const { total, unjustified } = inspectReport(report(
    '<testcase name="ok" classname="a.B"/>'
    + '<testcase name="semMotivo" classname="a.B"><skipped/></testcase>'
    + '<testcase name="semIssue" classname="a.B"><skipped message="instável"/></testcase>'));
  assert.equal(total, 3);
  assert.deepEqual(unjustified, [
    { test: 'a.B.semMotivo', reason: '(sem motivo)' },
    { test: 'a.B.semIssue', reason: 'instável' },
  ]);
});

test('teste pulado com a issue na justificativa passa', () => {
  const { unjustified } = inspectReport(report(
    '<testcase name="t" classname="a.B"><skipped message="depende do realm &quot;fithub&quot; DEV-0024"/></testcase>'));
  assert.deepEqual(unjustified, []);
});

test('falha de teste não é confundida com teste pulado', () => {
  const { total, unjustified } = inspectReport(report(
    '<testcase name="t" classname="a.B"><failure message="esperado 1"/></testcase>'));
  assert.equal(total, 1);
  assert.deepEqual(unjustified, []);
});

test('procura relatórios nas pastas do surefire, ignora node_modules e exige ao menos um teste', () => {
  const root = mkdtempSync(join(tmpdir(), 'skipped-'));
  try {
    assert.match(checkSkippedTests([root]).errors[0], /Nenhum teste/);
    assert.deepEqual(checkSkippedTests([root], { allowNone: true }).errors, []);

    mkdirSync(join(root, 'node_modules', 'x', 'surefire-reports'), { recursive: true });
    writeFileSync(join(root, 'node_modules', 'x', 'surefire-reports', 'TEST-x.xml'),
      report('<testcase name="t" classname="x"><skipped/></testcase>'));
    mkdirSync(join(root, 'svc', 'target', 'surefire-reports'), { recursive: true });
    writeFileSync(join(root, 'svc', 'target', 'surefire-reports', 'TEST-a.xml'), report('<testcase name="t" classname="a"/>'));
    writeFileSync(join(root, 'svc', 'target', 'outro.xml'), report('<testcase name="t" classname="o"><skipped/></testcase>'));
    assert.deepEqual(checkSkippedTests([root]), { reports: 1, total: 1, errors: [] });

    writeFileSync(join(root, 'svc', 'target', 'surefire-reports', 'TEST-b.xml'),
      report('<testcase name="pulado" classname="b"><skipped message="depois"/></testcase>'));
    assert.deepEqual(checkSkippedTests([root]).errors, ['Teste pulado sem issue na justificativa: b.pulado — depois']);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
