import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { changedManifests, withManifestAreas } from './ci-changes.mjs';
import { applyMutant, manifestAreas, runManifests, validateManifest } from './mutations.mjs';

/** Repositório temporário com um arquivo alvo e os manifestos dados. */
function withRepo(files, callback) {
  const root = mkdtempSync(join(tmpdir(), 'mutations-'));
  try {
    for (const [path, content] of Object.entries(files)) {
      mkdirSync(join(root, path, '..'), { recursive: true });
      writeFileSync(join(root, path), content);
    }
    return callback(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

const mutant = (overrides = {}) => ({
  id: 'M1', description: 'guarda removida', area: 'tools', file: 'src/guard.mjs',
  find: 'if (!ok) throw new Error();', replace: '', command: ['node', '--test'], ...overrides,
});
const manifest = (mutants, issue = 'DEV-0116') => ({ issue, mutants });
const GUARD = 'export function guard(ok) {\n  if (!ok) throw new Error();\n}\n';

// --- validação do manifesto ---

test('manifesto válido não tem problemas', () => {
  withRepo({ 'src/guard.mjs': GUARD }, (root) => {
    assert.deepEqual(validateManifest(manifest([mutant()]), 'mutations/DEV-0116.json', root), []);
  });
});

test('manifesto fora do formato é recusado com o motivo', () => {
  withRepo({ 'src/guard.mjs': GUARD }, (root) => {
    const check = (value, file = 'mutations/DEV-0116.json') => validateManifest(value, file, root).join(' | ');
    assert.match(check(manifest([mutant()]), 'mutations/tarefa.json'), /DEV-NNNN/);
    assert.match(check(manifest([mutant()], 'DEV-0001')), /"issue" precisa ser "DEV-0116"/);
    assert.match(check(manifest([])), /ao menos um mutante/);
    assert.match(check(manifest([mutant(), mutant()])), /"id" repetido/);
    assert.match(check(manifest([mutant({ area: 'web' })])), /"area"/);
    for (const file of ['../fora.mjs', '/etc/passwd', 'src\\guard.mjs', 'src/../../fora.mjs']) {
      assert.match(check(manifest([mutant({ file })])), /dentro do repositório/, file);
    }
    assert.match(check(manifest([mutant({ file: 'src/nao-existe.mjs' })])), /arquivo não existe/);
    assert.match(check(manifest([mutant({ find: '' })])), /"find" vazio/);
    assert.match(check(manifest([mutant({ replace: mutant().find })])), /"replace" precisa ser texto diferente/);
    assert.match(check(manifest([mutant({ command: ['bash', '-c', 'exit 1'] })])), /fora da lista/);
    assert.match(check(manifest([mutant({ command: [] })])), /lista de argumentos/);
    assert.match(check(manifest([mutant({ description: ' ' })])), /"description" vazio/);
  });
});

// --- aplicação do mutante ---

test('mutante morto: o comando falha com o mutante e o arquivo volta ao original', () => {
  withRepo({ 'src/guard.mjs': GUARD }, (root) => {
    let seen;
    const result = applyMutant(mutant(), { root, run: () => { seen = readFileSync(join(root, 'src/guard.mjs'), 'utf8'); return 1; } });
    assert.equal(result.outcome, 'KILLED');
    assert.ok(!seen.includes('throw'), 'o comando roda com o mutante aplicado');
    assert.equal(readFileSync(join(root, 'src/guard.mjs'), 'utf8'), GUARD);
  });
});

test('mutante que sobrevive é reportado', () => {
  withRepo({ 'src/guard.mjs': GUARD }, (root) => {
    const result = applyMutant(mutant(), { root, run: () => 0 });
    assert.equal(result.outcome, 'SURVIVED');
    assert.equal(readFileSync(join(root, 'src/guard.mjs'), 'utf8'), GUARD);
  });
});

test('trecho que não aparece exatamente uma vez não é aplicado', () => {
  withRepo({ 'src/guard.mjs': `${GUARD}${GUARD}` }, (root) => {
    let ran = false;
    assert.equal(applyMutant(mutant(), { root, run: () => { ran = true; return 1; } }).outcome, 'ANCHOR_NOT_UNIQUE');
    assert.equal(applyMutant(mutant({ find: 'nao existe' }), { root, run: () => 1 }).outcome, 'ANCHOR_NOT_UNIQUE');
    assert.equal(ran, false);
  });
});

test('arquivo é restaurado mesmo se o comando lançar, e restauração que falha é reportada', () => {
  withRepo({ 'src/guard.mjs': GUARD }, (root) => {
    assert.throws(() => applyMutant(mutant(), { root, run: () => { throw new Error('quebrou'); } }), /quebrou/);
    assert.equal(readFileSync(join(root, 'src/guard.mjs'), 'utf8'), GUARD);
    // Uma escrita que ignora a restauração (segunda chamada) deixa o mutante no arquivo: isso é falha.
    let writes = 0;
    const write = (path, content) => { writes += 1; if (writes === 1) writeFileSync(path, content); };
    assert.equal(applyMutant(mutant(), { root, run: () => 1, write }).outcome, 'NOT_RESTORED');
  });
});

test('trecho com LF casa com arquivo em CRLF (checkout no Windows) e o original volta byte a byte', () => {
  const crlf = GUARD.replace(/\n/g, '\r\n');
  withRepo({ 'src/guard.mjs': crlf }, (root) => {
    const result = applyMutant(mutant({ find: '{\n  if (!ok) throw new Error();', replace: '{' }), { root, run: () => 1 });
    assert.equal(result.outcome, 'KILLED');
    assert.equal(readFileSync(join(root, 'src/guard.mjs'), 'utf8'), crlf);
  });
});

// --- manifestos e áreas ---

test('roda só os mutantes da área e junta os erros de todos os manifestos', () => {
  withRepo({
    'src/guard.mjs': GUARD,
    'mutations/DEV-0116.json': JSON.stringify(manifest([mutant(), mutant({ id: 'M2', area: 'java' })])),
    'mutations/DEV-0117.json': '{ não é json',
  }, (root) => {
    const ran = [];
    const { results, errors } = runManifests(['mutations/DEV-0116.json', 'mutations/DEV-0117.json'], 'tools', {
      root, run: (command) => { ran.push(command); return 1; },
    });
    assert.deepEqual(results.map(({ id, outcome }) => [id, outcome]), [['M1', 'KILLED']]);
    assert.equal(ran.length, 1);
    assert.equal(errors.length, 1);
    assert.match(errors[0], /DEV-0117\.json: JSON inválido/);
  });
});

test('mutante sobrevivente vira erro do manifesto', () => {
  withRepo({ 'src/guard.mjs': GUARD, 'mutations/DEV-0116.json': JSON.stringify(manifest([mutant()])) }, (root) => {
    const { errors } = runManifests(['mutations/DEV-0116.json'], 'tools', { root, run: () => 0 });
    assert.match(errors[0], /M1: SURVIVED/);
  });
});

test('manifestos alterados ligam as áreas que citam; manifesto ilegível liga todas', () => {
  withRepo({
    'mutations/DEV-0116.json': JSON.stringify(manifest([mutant({ area: 'java' }), mutant({ id: 'M2', area: 'apps' })])),
    'mutations/DEV-0117.json': 'quebrado',
    'mutations/notas.md': '# não é manifesto',
  }, (root) => {
    assert.deepEqual(manifestAreas(['mutations/DEV-0116.json'], root), ['apps', 'java']);
    assert.deepEqual(manifestAreas(['mutations/DEV-0117.json'], root), ['apps', 'java', 'tools']);
    assert.deepEqual(changedManifests(['mutations/DEV-0116.json', 'mutations/notas.md', 'mutations/DEV-0999.json', 'src/x.ts'], root),
      ['mutations/DEV-0116.json']);
    assert.deepEqual(withManifestAreas({ java: false, apps: false, infra: false }, ['java', 'tools']), { java: true, apps: false, infra: false });
  });
});
