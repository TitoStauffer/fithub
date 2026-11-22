// Testes do verificador de documentação (DEV-0010): cada guarda falha no cenário que ela proíbe.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { runChecks } from './check-docs.mjs';

function repo(files) {
  const root = mkdtempSync(join(tmpdir(), 'fithub-check-docs-'));
  const git = (...args) => execFileSync('git', ['-c', 'user.name=teste', '-c', 'user.email=teste@example.invalid', '-c', 'core.longpaths=true', ...args], { cwd: root, stdio: 'pipe', encoding: 'utf8' });
  git('init', '-q');
  const write = (entries) => {
    for (const [path, content] of Object.entries(entries)) {
      mkdirSync(dirname(join(root, path)), { recursive: true });
      writeFileSync(join(root, path), content);
    }
  };
  write(files);
  git('add', '-A');
  git('commit', '-q', '-m', 'inicial');
  return { root, git, write, cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

test('accepts a tree with short paths, valid links and no history file', (t) => {
  const r = repo({ 'README.md': '[guia](docs/guia.md), [site](https://example.com), [topo](#topo), `[exemplo](nao/existe.md)`', 'docs/guia.md': '[volta](../README.md) [pasta](../docs)' });
  t.after(r.cleanup);
  assert.deepEqual(runChecks(r.root).errors, []);
});

test('rejects a tracked path above 200 characters', (t) => {
  const longPath = `docs/${'a'.repeat(100)}/${'b'.repeat(100)}.md`;
  const r = repo({ [longPath]: 'x' });
  t.after(r.cleanup);
  const { errors, longest } = runChecks(r.root);
  assert.equal(longest, longPath.length);
  assert.match(errors.join('\n'), /caminho com 209 caracteres \(máximo 200\)/);
});

test('rejects a relative link to a missing file, outside code blocks only', (t) => {
  const r = repo({ 'AGENTS.md': '[spec](MVP_SPECIFICATION.md)\n\n```\n[ignorado](bloco.md)\n```', 'legacy/README.md': '[antigo](sumiu.md)' });
  t.after(r.cleanup);
  assert.deepEqual(runChecks(r.root).errors, ['AGENTS.md: link para caminho inexistente: MVP_SPECIFICATION.md']);
});

function withHistory(table, sha) {
  return `# Histórico\n\nÚltimo commit: **\`${sha}\`**.\n\n## O que foi removido\n\n| Caminho | O que era |\n|---|---|\n${table}\n\n## Como recuperar\n\n\`git show\`\n`;
}

test('accepts a history whose removed paths exist in the cited commit and not in HEAD', (t) => {
  const r = repo({ 'docs/antigo/a.md': 'a', 'SPEC.md': 's', 'README.md': 'r' });
  t.after(r.cleanup);
  const sha = r.git('rev-parse', '--short', 'HEAD').trim();
  r.git('rm', '-q', '-r', 'docs/antigo', 'SPEC.md');
  r.write({ 'docs/HISTORICO.md': withHistory('| `docs/antigo/` | pasta |\n| `SPEC.md` | arquivo |', sha) });
  r.git('add', '-A');
  r.git('commit', '-q', '-m', 'remove');
  const result = runChecks(r.root);
  assert.deepEqual(result.errors, []);
  assert.equal(result.history.checked, 2);
});

test('rejects a history path missing from the cited commit or still present in HEAD', (t) => {
  const r = repo({ 'docs/antigo/a.md': 'a', 'KEEP.md': 'k' });
  t.after(r.cleanup);
  const sha = r.git('rev-parse', '--short', 'HEAD').trim();
  r.git('rm', '-q', '-r', 'docs/antigo');
  r.write({ 'docs/HISTORICO.md': withHistory('| `docs/antigo/` | ok |\n| `NUNCA.md` | inventado |\n| `KEEP.md` | não removido |', sha) });
  r.git('add', '-A');
  r.git('commit', '-q', '-m', 'remove');
  const errors = runChecks(r.root).errors.join('\n');
  assert.match(errors, /NUNCA\.md não existe no commit/);
  assert.match(errors, /KEEP\.md ainda existe no HEAD/);
  assert.doesNotMatch(errors, /docs\/antigo/);
});

test('rejects a history file without the recovery commit or without removed paths', (t) => {
  const r = repo({ 'docs/HISTORICO.md': '# Histórico\n\nSem commit.\n' });
  t.after(r.cleanup);
  assert.match(runChecks(r.root).errors.join('\n'), /não cita o commit de recuperação/);
  r.write({ 'docs/HISTORICO.md': withHistory('', 'abc1234') });
  r.git('add', '-A');
  r.git('commit', '-q', '-m', 'vazio');
  assert.match(runChecks(r.root).errors.join('\n'), /não lista caminhos/);
});
