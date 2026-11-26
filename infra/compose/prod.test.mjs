// Guardas do compose de produção, das rotas do Traefik e do workflow de deploy (DEV-0110, ADR-014). Lê o compose
// resolvido pelo próprio Docker Compose, com os marcadores do .env.prod.example. Uso, a partir da raiz:
//   node --test infra/compose/prod.test.mjs
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const COMPOSE = 'infra/compose/docker-compose.prod.yml';
// Quebra de linha normalizada: num clone do Windows, o Git grava os arquivos com CRLF.
const read = (path) => readFileSync(new URL(path, root), 'utf8').replace(/\r\n/g, '\n');
const example = read('infra/compose/.env.prod.example');
const IMAGE_TAG = 'a'.repeat(40);
const SERVICES = { 'account-service': 8081, 'client-service': 8082, 'training-service': 8083, 'nutrition-service': 8084 };
// Todo segredo do .env.prod.example é obrigatório no compose.
const SECRETS = example
  .split('\n')
  .filter((line) => /^[A-Z_]+=/.test(line))
  .map((line) => line.slice(0, line.indexOf('=')));

// O compose resolvido, num diretório temporário com o .env dado; devolve o JSON ou o erro do Compose.
function compose(envText, env = { FITHUB_IMAGE_TAG: IMAGE_TAG }) {
  const dir = mkdtempSync(join(tmpdir(), 'fithub-prod-'));
  try {
    const envFile = join(dir, '.env');
    writeFileSync(envFile, envText);
    const clean = Object.fromEntries(Object.entries(process.env).filter(([key]) => !SECRETS.includes(key) && !key.startsWith('FITHUB_')));
    const result = spawnSync('docker', ['compose', '-f', COMPOSE, '--env-file', envFile, 'config', '--format', 'json'], {
      cwd: root,
      env: { ...clean, ...env },
      encoding: 'utf8',
    });
    return { ok: result.status === 0, config: result.status === 0 ? JSON.parse(result.stdout) : null, error: result.stderr };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const resolved = compose(example);

test('o compose de produção é válido com o .env de exemplo', () => {
  assert.ok(resolved.ok, resolved.error);
});

test('só o Traefik publica portas públicas (80 e 443); o resto, no máximo em 127.0.0.1', () => {
  for (const [name, service] of Object.entries(resolved.config.services)) {
    for (const port of service.ports ?? []) {
      if (name === 'traefik') {
        assert.ok([80, 443].includes(port.target), `traefik publica a porta ${port.target}`);
      } else {
        assert.equal(port.host_ip, '127.0.0.1', `${name} publica a porta ${port.target} fora do 127.0.0.1`);
      }
    }
  }
  assert.deepEqual(resolved.config.services.traefik.ports.map((port) => port.target).sort(), [443, 80]);
});

test('o Traefik não monta o socket do Docker', () => {
  for (const volume of resolved.config.services.traefik.volumes ?? []) {
    assert.doesNotMatch(volume.source ?? '', /docker\.sock/);
  }
});

test('cada segredo do .env é obrigatório: sem ele, o compose não sobe', () => {
  assert.ok(SECRETS.length >= 12, `poucos segredos no exemplo: ${SECRETS.join(', ')}`);
  for (const secret of SECRETS) {
    const without = example
      .split('\n')
      .filter((line) => !line.startsWith(`${secret}=`))
      .join('\n');
    const result = compose(without);
    assert.equal(result.ok, false, `o compose subiu sem ${secret}`);
    assert.match(result.error, new RegExp(secret), `o erro sem ${secret} não diz qual falta`);
  }
});

// Quem pode receber cada credencial: o dono e o container que cria a credencial (Code Review do PR #322).
const CREDENTIAL_RECEIVERS = {
  ACCOUNT_DB_PASSWORD: ['account-service', 'postgres'],
  CLIENT_DB_PASSWORD: ['client-service', 'postgres'],
  TRAINING_DB_PASSWORD: ['postgres', 'training-service'],
  NUTRITION_DB_PASSWORD: ['nutrition-service', 'postgres'],
  KEYCLOAK_DB_PASSWORD: ['keycloak', 'postgres'],
  POSTGRES_PASSWORD: ['backup', 'postgres'],
  RABBITMQ_ADMIN_PASSWORD: ['rabbitmq', 'rabbitmq-users'],
  ACCOUNT_RABBITMQ_PASSWORD: ['account-service', 'rabbitmq-users'],
  CLIENT_RABBITMQ_PASSWORD: ['client-service', 'rabbitmq-users'],
  TRAINING_RABBITMQ_PASSWORD: ['rabbitmq-users', 'training-service'],
  NUTRITION_RABBITMQ_PASSWORD: ['nutrition-service', 'rabbitmq-users'],
  CLIENT_S3_ACCESS_KEY: ['client-service', 'seaweedfs'],
  CLIENT_S3_SECRET_KEY: ['client-service', 'seaweedfs'],
  NUTRITION_S3_ACCESS_KEY: ['nutrition-service', 'seaweedfs'],
  NUTRITION_S3_SECRET_KEY: ['nutrition-service', 'seaweedfs'],
  FITHUB_ACCOUNT_SERVICE_SECRET: ['account-service', 'keycloak'],
  KEYCLOAK_ADMIN_PASSWORD: ['keycloak'],
  // O mesmo SMTP manda o convite (client-service) e a verificação do cadastro (Keycloak, DEV-0176).
  SMTP_PASSWORD: ['client-service', 'keycloak'],
};

test('cada credencial chega só ao dono e a quem a cria', () => {
  // Um valor diferente por credencial, para saber quem recebeu qual.
  const distinct = example.replace(/^([A-Z0-9_]+)=<[a-z-]+>$/gm, (_, name) => `${name}=valor-de-${name.toLowerCase()}`);
  const { config } = compose(distinct);
  for (const [name, expected] of Object.entries(CREDENTIAL_RECEIVERS)) {
    const value = `valor-de-${name.toLowerCase()}`;
    const receivers = Object.entries(config.services)
      .filter(([, service]) => Object.values(service.environment ?? {}).includes(value))
      .map(([service]) => service)
      .sort();
    assert.deepEqual(receivers, expected, `${name} chegou a ${receivers.join(', ')}`);
  }
  assert.doesNotMatch(read(COMPOSE), /FITHUB_DB_PASSWORD|\$\{RABBITMQ_(USER|PASSWORD)|\$\{S3_(ACCESS|SECRET)_KEY/);
});

test('cada serviço entra no RabbitMQ com o próprio usuário, que só publica na própria exchange', () => {
  for (const name of Object.keys(SERVICES)) {
    assert.equal(resolved.config.services[name].environment.RABBITMQ_USER, name.replace('-service', ''));
  }
  const script = read('infra/compose/rabbitmq/init-users.sh');
  assert.ok(script.includes('--write "^($service\\..*|fithub\\.$service)\\$"'), 'permissão de escrita do serviço');
  assert.ok(script.includes('--tags ""'), 'usuário de serviço sem tag de administração');
});

test('cada identidade S3 só tem o bucket do seu serviço', () => {
  const script = read('infra/compose/seaweedfs/start.sh');
  assert.ok(script.includes('\n  identity client-service "$client_key" "$client_secret" fithub-photos\n'));
  assert.ok(script.includes('\n  identity nutrition-service "$nutrition_key" "$nutrition_secret" fithub-meal-photos\n'));
  assert.equal((script.match(/\n {2}identity /g) ?? []).length, 2);
  // Só o gateway S3 atende a rede; o filer, o master e o volume ficam no loopback, sem credencial a conferir.
  assert.ok(script.includes('-ip=127.0.0.1 -ip.bind=127.0.0.1 -s3.ip.bind=0.0.0.0'), 'SeaweedFS fora do loopback');
});

test('o console do Keycloak abre pelo túnel SSH, não pelo domínio público', () => {
  assert.equal(resolved.config.services.keycloak.environment.KC_HOSTNAME_ADMIN, 'http://localhost:8080/auth');
});

test('o realm de produção recebe o endereço da web e o SMTP de produção (DEV-0176)', () => {
  const keycloak = resolved.config.services.keycloak.environment;
  assert.equal(keycloak.FITHUB_WEB_URL, 'https://<dominio>');
  assert.equal(keycloak.FITHUB_SMTP_AUTH, 'true');
  assert.equal(keycloak.FITHUB_SMTP_STARTTLS, 'true');
  assert.equal(keycloak.FITHUB_SMTP_HOST, '<host-smtp>');
  assert.equal(resolved.config.services.web.image, `ghcr.io/titostauffer/fithub-web:${IMAGE_TAG}`);
  assert.equal(resolved.config.services.web.ports, undefined);
});

test('a web fica em /, com a menor prioridade: /api, /auth e os buckets nunca caem nela', () => {
  const dynamic = read('infra/traefik/dynamic.yml');
  const router = dynamic.match(/\n {4}web:\n([\s\S]*?)\n {4}[a-z]+:\n|\n {4}web:\n([\s\S]*?)\n\n/);
  assert.ok(router, 'sem a rota da web');
  const body = router[1] ?? router[2];
  assert.match(body, /rule: Host\(`\{\{ env "FITHUB_DOMAIN" \}\}`\)\n/);
  assert.match(body, /priority: 1\n/);
  assert.match(dynamic, /url: "http:\/\/web:8080"/);
});

test('a tag da imagem dos serviços é obrigatória e vem do disparo do deploy', () => {
  assert.equal(compose(example, {}).ok, false);
  for (const name of Object.keys(SERVICES)) {
    assert.equal(resolved.config.services[name].image, `ghcr.io/titostauffer/fithub-${name}:${IMAGE_TAG}`);
  }
});

test('o .env de exemplo só tem marcadores, nenhum valor real', () => {
  const allowed = new Set(['SMTP_PORT=587']);
  for (const line of example.split('\n').filter((candidate) => /^[A-Z_]+=/.test(candidate))) {
    if (allowed.has(line)) continue;
    assert.match(line.slice(line.indexOf('=') + 1), /<[a-z-]+>/, `valor que não é marcador: ${line}`);
  }
});

test('toda imagem tem versão fixa, nunca latest', () => {
  for (const [name, service] of Object.entries(resolved.config.services)) {
    assert.match(service.image, /:[^:/]+$/, `${name} sem versão`);
    assert.doesNotMatch(service.image, /:latest$/, `${name} com latest`);
  }
});

test('cada rota do Traefik vai para o serviço certo, só em HTTPS', () => {
  const dynamic = read('infra/traefik/dynamic.yml');
  for (const [name, port] of Object.entries(SERVICES)) {
    const short = name.replace('-service', '');
    const router = dynamic.match(new RegExp(`\\n    ${short}:\\n([\\s\\S]*?)\\n    [a-z]+:\\n`));
    assert.ok(router, `sem a rota ${short}`);
    assert.match(router[1], new RegExp(`PathPrefix\\(\`/api/${short}\`\\)`));
    assert.match(router[1], new RegExp(`middlewares: \\[strip-api-${short}, security-headers\\]`));
    assert.match(dynamic, new RegExp(`prefixes: \\["/api/${short}"\\]`));
    assert.match(dynamic, new RegExp(`url: "http://${name}:${port}"`));
  }
  assert.doesNotMatch(dynamic, /entryPoints: \[web\]/);
  assert.equal(dynamic.match(/entryPoints: \[websecure\]/g).length, 7);
});

test('a borda do Keycloak tem só o realm fithub e os recursos, sem o console de administração', () => {
  const dynamic = read('infra/traefik/dynamic.yml');
  const rule = dynamic.match(/\n    keycloak:\n      rule: (.*)\n/)[1];
  const prefixes = [...rule.matchAll(/PathPrefix\(`([^`]*)`\)/g)].map((match) => match[1]);
  assert.deepEqual(prefixes, ['/auth/realms/fithub/', '/auth/resources/']);
});

test('backup diário agendado, com retenção de 30 dias', () => {
  const backup = resolved.config.services.backup;
  assert.deepEqual(backup.command, ['sh', '/scripts/cron-entrypoint.sh']);
  assert.equal(backup.environment.BACKUP_RETENTION_DAYS, '30');
  assert.equal(backup.environment.BACKUP_SCHEDULE, '0 3 * * *');
});

test('logs e traces com retenção de 30 dias (ADR-012)', () => {
  for (const name of Object.keys(SERVICES)) {
    assert.equal(resolved.config.services[name].environment.LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY, '30', name);
  }
  const loki = read('infra/observability/loki-config.yaml');
  assert.match(loki, /\nlimits_config:\n  retention_period: 720h\n/);
  assert.match(loki, /\n  retention_enabled: true\n/);
  // Tempo 3: o backend-scheduler agenda a retenção e o backend-worker aplica.
  const tempo = read('infra/observability/tempo-config.yaml');
  assert.match(tempo, /\nbackend_scheduler:\n  provider:\n    compaction:\n      compaction:\n        block_retention: 720h\n/);
  assert.match(tempo, /\nbackend_worker:\n  compaction:\n    block_retention: 720h\n/);
});

test('o workflow de deploy é só manual, só leitura e confere o host', () => {
  const workflow = read('.github/workflows/deploy.yml');
  assert.match(workflow, /\non:\n  workflow_dispatch:\n/);
  assert.doesNotMatch(workflow, /\n  (push|pull_request|pull_request_target|schedule|workflow_run):/);
  assert.match(workflow, /\npermissions:\n  contents: read\n\n/);
  assert.match(workflow, /StrictHostKeyChecking yes/);
  assert.doesNotMatch(workflow, /StrictHostKeyChecking (no|accept-new)/);
  assert.match(workflow, /\^\[0-9a-f\]\{40\}\$/);
  for (const uses of workflow.matchAll(/uses: (\S+)/g)) {
    assert.match(uses[1], /@[0-9a-f]{40}$/, `ação sem SHA fixo: ${uses[1]}`);
  }
});

