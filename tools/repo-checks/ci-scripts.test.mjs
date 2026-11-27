import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { execFileSync } from 'node:child_process';

import { changedFiles, classifyChanges, classifyServices, discoverServices, formatOutputs } from './ci-changes.mjs';
import { isNonRootUser } from './check-image-user.mjs';
import { checkSkippedTests, inspectReport } from './check-skipped-tests.mjs';

// --- ci-changes ---

test('cada área roda só quando um caminho dela muda', () => {
  assert.deepEqual(classifyChanges(['services/client-service/src/main/java/A.java']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['libs/java-commons/pom.xml']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['pom.xml']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['apps/web/src/App.tsx']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['pnpm-lock.yaml']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['infra/compose/docker-compose.dev.yml']), { java: false, apps: false, infra: true });
  assert.deepEqual(classifyChanges(['.github/workflows/deploy.yml']), { java: false, apps: false, infra: true });
});

// Code Review do PR #187: o teste Java lê o contrato OpenAPI e o teste do api-types lê os Dockerfiles.
// Mudar um desses arquivos precisa ligar a área que o lê, senão o check exigido fica verde sem conferir.
test('contrato OpenAPI liga o Java e Dockerfile de serviço liga os apps', () => {
  assert.deepEqual(classifyChanges(['packages/api-types/openapi/account-service.json']), { java: true, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['services/training-service/Dockerfile']), { java: true, apps: true, infra: false });
  // O resto de packages/ e de services/ continua só na própria área.
  assert.deepEqual(classifyChanges(['packages/api-types/src/index.ts']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['services/training-service/src/main/java/A.java']), { java: true, apps: false, infra: false });
});

// DEV-0129: a configuração do padrão de código liga a área que ela confere.
test('regras do Checkstyle ligam o Java e a configuração do Prettier liga os apps', () => {
  assert.deepEqual(classifyChanges(['config/checkstyle/checkstyle.xml']), { java: true, apps: false, infra: false });
  assert.deepEqual(classifyChanges(['.editorconfig']), { java: false, apps: true, infra: false });
  assert.deepEqual(classifyChanges(['.prettierrc.json']), { java: false, apps: true, infra: false });
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

test('Maven Wrapper e configuração do Maven ligam a área Java', () => {
  assert.equal(classifyChanges(['.mvn/maven.config']).java, true);
  assert.equal(classifyChanges(['mvnw.cmd']).java, true);
});

// Repositório git temporário: base com um commit, `change` monta o commit do HEAD.
function withRepo(setup, change, check) {
  const repo = mkdtempSync(join(tmpdir(), 'ci-changes-'));
  const git = (...args) => execFileSync('git', ['-c', 'user.name=ci', '-c', 'user.email=ci@example.invalid', ...args], { cwd: repo, encoding: 'utf8' }).trim();
  const write = (path, content) => {
    mkdirSync(join(repo, ...path.split('/').slice(0, -1)), { recursive: true });
    writeFileSync(join(repo, ...path.split('/')), content);
  };
  try {
    git('init', '-q');
    // O padrão do git, explícito: o teste não depende da configuração global de quem roda.
    git('config', 'core.quotePath', 'true');
    setup({ git, write });
    git('add', '-A');
    git('commit', '-q', '--allow-empty', '-m', 'base');
    const base = git('rev-parse', 'HEAD');
    change({ git, write });
    git('add', '-A');
    git('commit', '-q', '-m', 'change');
    check(changedFiles(base, git('rev-parse', 'HEAD'), { cwd: repo }).filter(Boolean).sort());
  } finally {
    rmSync(repo, { recursive: true, force: true });
  }
}

// Code Review do PR #179: com a detecção de renomeação do git, mover um arquivo de services/ para fora
// das áreas mostrava só o caminho novo, e o Java Services passava sem compilar o serviço que o perdeu.
test('arquivo movido para fora da área ainda liga a área de origem', () => {
  withRepo(
    ({ write }) => write('services/a/Foo.java', 'class Foo {\n  int a = 1;\n  int b = 2;\n}\n'),
    ({ git }) => { git('mv', 'services/a/Foo.java', 'legacy-Foo.java'); },
    (files) => {
      assert.deepEqual(files, ['legacy-Foo.java', 'services/a/Foo.java']);
      assert.equal(classifyChanges(files).java, true);
    },
  );
});

// Code Review do PR #179 (rodada 2): com core.quotePath, o git devolvia
// "services/a/A\303\247\303\243o.java", entre aspas, e o caminho não casava com services/.
test('caminho com acento chega sem aspas nem escapes e liga a área', () => {
  withRepo(
    () => {},
    ({ write }) => write('services/a/V2__criação.sql', 'select 1;\n'),
    (files) => {
      assert.deepEqual(files, ['services/a/V2__criação.sql']);
      assert.equal(classifyChanges(files).java, true);
    },
  );
});

test('SHA fora do formato é recusado antes de chamar o git', () => {
  assert.throws(() => changedFiles('main', 'a'.repeat(40)), /SHA inválido/);
  assert.throws(() => changedFiles('a'.repeat(40), '--output=x'), /SHA inválido/);
});

// --- imagens (DEV-0017) ---

const SERVICES = ['account-service', 'client-service', 'nutrition-service', 'training-service'];

test('só o serviço alterado gera imagem', () => {
  assert.deepEqual(classifyServices(['services/client-service/src/main/java/A.java'], SERVICES), ['client-service']);
  assert.deepEqual(classifyServices(['services/client-service/Dockerfile', 'services/training-service/pom.xml'], SERVICES),
    ['client-service', 'training-service']);
  // Prefixo de outro serviço não conta: services/client-service-x/ não é o client-service.
  assert.deepEqual(classifyServices(['services/client-service-x/A.java'], SERVICES), []);
});

test('documentação, apps e o README de services não geram imagem', () => {
  assert.deepEqual(classifyServices(['docs/00-oficial/PRD.md', 'apps/web/src/App.tsx', 'services/README.md'], SERVICES), []);
});

test('build compartilhado, contexto das imagens, CI ou lista vazia geram todas as imagens', () => {
  for (const files of [['pom.xml'], ['libs/java-commons/pom.xml'], ['.dockerignore'], ['.mvn/maven.config'],
    ['.github/workflows/ci.yml'], ['tools/repo-checks/ci-changes.mjs'], []]) {
    assert.deepEqual(classifyServices(files, SERVICES), SERVICES, JSON.stringify(files));
  }
});

test('serviços com imagem são as pastas de services/ com Dockerfile, e o nome é validado', () => {
  const root = mkdtempSync(join(tmpdir(), 'services-'));
  try {
    mkdirSync(join(root, 'services', 'b-service'), { recursive: true });
    writeFileSync(join(root, 'services', 'b-service', 'Dockerfile'), 'FROM scratch\n');
    mkdirSync(join(root, 'services', 'a-service'), { recursive: true });
    writeFileSync(join(root, 'services', 'a-service', 'Dockerfile'), 'FROM scratch\n');
    mkdirSync(join(root, 'services', 'sem-imagem'), { recursive: true });
    writeFileSync(join(root, 'services', 'README.md'), '# services\n');
    assert.deepEqual(discoverServices(root), ['a-service', 'b-service']);

    mkdirSync(join(root, 'services', 'Mau Nome'), { recursive: true });
    writeFileSync(join(root, 'services', 'Mau Nome', 'Dockerfile'), 'FROM scratch\n');
    assert.throws(() => discoverServices(root), /Nome de serviço inválido/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('os quatro serviços do repositório têm Dockerfile', () => {
  assert.deepEqual(discoverServices(join(import.meta.dirname, '..', '..')), SERVICES);
});

test('imagem que roda como root é recusada', () => {
  for (const user of ['', '  ', 'root', '0', '000', 'root:root', '10001:0', '0:10001', 'fithub:root', undefined]) {
    assert.equal(isNonRootUser(user), false, JSON.stringify(user));
  }
  for (const user of ['10001', '10001:10001', 'fithub', 'fithub:fithub', '1000:1000']) {
    assert.equal(isNonRootUser(user), true, user);
  }
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

// DEV-0020: o JUnit do Vitest grava só <skipped/>; sem motivo gravado, vale a issue no nome do teste.
// Motivo gravado continua decidindo: issue só no nome não salva um @Disabled("instável").
test('sem motivo gravado, a issue no nome do teste justifica o pulo', () => {
  const { unjustified } = inspectReport(report(
    '<testcase classname="src/a.test.tsx" name="depende do realm DEV-0024"><skipped/></testcase>'
    + '<testcase classname="src/a.test.tsx" name="sem issue"><skipped/></testcase>'
    + '<testcase classname="a.B" name="nome DEV-0024"><skipped message="instável"/></testcase>'));
  assert.deepEqual(unjustified, [
    { test: 'src/a.test.tsx.sem issue', reason: '(sem motivo)' },
    { test: 'a.B.nome DEV-0024', reason: 'instável' },
  ]);
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

// --- Dockerfiles dos serviços (DEV-0155) ---

test('o build de cada serviço monta o repositório Maven opcional do contexto m2, vazio por padrão', async () => {
  const { readdirSync, readFileSync, existsSync } = await import('node:fs');
  const services = readdirSync('services').filter((name) => existsSync(join('services', name, 'Dockerfile')));
  assert.ok(services.length >= 4);
  for (const service of services) {
    const dockerfile = readFileSync(join('services', service, 'Dockerfile'), 'utf8').replace(/\r\n/g, '\n');
    // Sem --build-context m2=..., vale o estágio vazio, e o docker build local baixa tudo, como antes.
    assert.match(dockerfile, /^FROM scratch AS m2$/m, `${service}: estágio m2 vazio`);
    const run = [
      'RUN --mount=type=bind,from=m2,target=/root/.m2/repository,rw \\',
      `    mvn -B -ntp -q -pl services/${service} -am package -DskipTests`,
    ].join('\n');
    assert.ok(dockerfile.includes(`\n${run}\n`), `${service}: o mvn package usa o repositório do contexto m2`);
  }
});
