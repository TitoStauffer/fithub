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
  // Pagamento da assinatura (DEV-0100): só o account-service fala com o Asaas.
  ASAAS_API_KEY: ['account-service'],
  ASAAS_WEBHOOK_TOKEN: ['account-service'],
};

test('cada credencial chega só ao dono e a quem a cria', () => {
  // Um valor diferente por credencial, para saber quem recebeu qual.
  // O marcador pode vir entre aspas simples, como a chave do Asaas, que começa com $ (DEV-0181).
  const distinct = example.replace(/^([A-Z0-9_]+)=('?)<[a-z-]+>\2$/gm, (_, name, quote) => `${name}=${quote}valor-de-${name.toLowerCase()}${quote}`);
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
  // configure também vale para apagar: só as próprias filas e a própria exchange (DEV-0177).
  assert.ok(script.includes('--configure "^($service\\..*|fithub\\.$service)\\$"'), 'permissão de configurar do serviço');
  assert.ok(
    script.includes('admin declare exchange --name "fithub.$service" --type topic --durable true'),
    'exchanges declaradas pelo administrador',
  );
  for (const name of Object.keys(SERVICES)) {
    assert.equal(resolved.config.services[name].environment.FITHUB_MESSAGING_DECLARE_SOURCE_EXCHANGES, 'false', name);
  }
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

test('as métricas de sucesso saem para o otel-lgtm, e o painel está montado (DEV-0113)', () => {
  for (const name of Object.keys(SERVICES)) {
    const environment = resolved.config.services[name].environment;
    assert.equal(environment.MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED, 'true', name);
    assert.equal(environment.MANAGEMENT_OTLP_METRICS_EXPORT_URL, 'http://otel-lgtm:4318/v1/metrics', name);
  }
  const targets = resolved.config.services['otel-lgtm'].volumes.map((volume) => volume.target);
  assert.ok(targets.includes('/otel-lgtm/grafana/conf/provisioning/dashboards/fithub.yaml'));
  assert.ok(targets.includes('/otel-lgtm/fithub-metricas.json'));
});

test('a web fica em /, com a menor prioridade: /api, /auth e os buckets nunca caem nela', () => {
  const dynamic = read('infra/traefik/dynamic.yml');
  const router = dynamic.match(/\n {4}web:\n([\s\S]*?)\n {4}[a-z]+:\n|\n {4}web:\n([\s\S]*?)\n\n/);
  assert.ok(router, 'sem a rota da web');
  const body = router[1] ?? router[2];
  assert.match(body, /rule: Host\(`\{\{ env "FITHUB_DOMAIN" \}\}`\)\n/);
  assert.match(body, /priority: 1\n/);
  assert.match(dynamic, /url: "http:\/\/web:8080"/);
  // A web e o Keycloak podem ser embutidos pela própria origem (iframes do keycloak-js); as APIs e os buckets
  // continuam com DENY.
  assert.match(body, /middlewares: \[same-origin-headers\]\n/);
  const keycloak = dynamic.match(/\n {4}keycloak:\n([\s\S]*?)\n {4}[a-z]+:\n/)[1];
  assert.match(keycloak, /middlewares: \[same-origin-headers\]\n/);
  const photos = dynamic.match(/\n {4}photos:\n([\s\S]*?)\n {4}[a-z]+:\n/)[1];
  assert.match(photos, /middlewares: \[security-headers\]\n/);
  const webHeaders = dynamic.match(/\n {4}same-origin-headers:\n([\s\S]*?)\n\n/)[1];
  assert.match(webHeaders, /customFrameOptionsValue: SAMEORIGIN\n/);
  assert.doesNotMatch(webHeaders, /frameDeny/);
  assert.match(webHeaders, /stsSeconds: 31536000\n/);
  assert.match(dynamic, /\n {4}security-headers:\n[\s\S]*?frameDeny: true\n/);
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

// DEV-0181: a chave do Asaas começa com $. Sem aspas simples no .env, o compose a lê como variável e a entrega vazia.
test('a chave do Asaas fica entre aspas simples no modelo e chega inteira, com o $, ao account-service', () => {
  assert.match(example, /^ASAAS_API_KEY='<[a-z-]+>'$/m);
  const withKey = (line) => compose(example.replace(/^ASAAS_API_KEY=.*$/m, () => line));
  // A saída do `compose config` escreve o $ literal como $$; o container recebe um $ só.
  const quoted = withKey("ASAAS_API_KEY='$aact_chave_de_teste'");
  assert.equal(quoted.config.services['account-service'].environment.ASAAS_API_KEY, '$$aact_chave_de_teste');
  // Sem as aspas, a chave vira uma variável vazia, e o compose de produção nem sobe (a chave é obrigatória).
  assert.equal(withKey('ASAAS_API_KEY=$aact_chave_de_teste').ok, false);
});

// DEV-0181: as contas de teste, o retorno do Expo Go e os dados de exemplo são só do ambiente local
// (docker-compose.app.yml e infra/dev-seed); nada disso chega à produção.
test('a produção não tem o seed do ambiente local nem a senha das contas de teste', () => {
  for (const [name, service] of Object.entries(resolved.config.services)) {
    assert.notEqual(name, 'seed');
    const mounts = (service.volumes ?? []).map((volume) => volume.source ?? '');
    assert.ok(!mounts.some((source) => source.includes('dev-seed')), `${name} monta o seed local`);
    assert.equal(service.environment?.FITHUB_SEED_PASSWORD, undefined, `${name} recebe a senha de teste`);
  }
});

// DEV-0181, Code Review do PR #361: o override do ambiente local passa variáveis aos serviços pelo nome; se um nome mudar
// no application.yml, o compose continua válido e o ambiente para de funcionar. As SPRING_* valem pelo relaxed binding.
test('o override do ambiente local só passa aos serviços variáveis que eles leem', () => {
  const result = spawnSync(
    'docker',
    ['compose', '-f', 'infra/compose/docker-compose.dev.yml', '-f', 'infra/compose/docker-compose.app.yml', 'config', '--format', 'json'],
    { cwd: root, encoding: 'utf8' },
  );
  assert.equal(result.status, 0, result.stderr);
  const services = JSON.parse(result.stdout).services;
  for (const service of Object.keys(SERVICES)) {
    const yml = read(`services/${service}/src/main/resources/application.yml`);
    for (const name of Object.keys(services[service].environment ?? {})) {
      if (name.startsWith('SPRING_')) continue;
      assert.ok(yml.includes(`\${${name}:`) || yml.includes(`\${${name}}`), `${service}: ${name} não aparece no application.yml`);
    }
  }
});

// DEV-0181, Code Review do PR #361: a borda local pode ficar aberta na rede (FITHUB_BIND=0.0.0.0), então, como a de
// produção, publica do Keycloak só o realm fithub e os recursos das telas, sem o console nem o realm master.
test('a borda local publica do Keycloak só o realm fithub e os recursos', () => {
  const edge = read('infra/compose/edge/nginx.conf');
  const keycloakRoutes = [...edge.matchAll(/location ([^{]*)\{([^}]*)\}/g)].filter(([, , body]) => body.includes('$keycloak'));
  assert.deepEqual(keycloakRoutes.map(([, route]) => route.trim()), ['~ ^/auth/(realms/fithub/|resources/)']);
  assert.match(edge, /location \/auth\/ \{\s*return 404;\s*\}/);
  const app = read('infra/compose/docker-compose.app.yml');
  assert.match(app, /"127\.0\.0\.1:\$\{FITHUB_KEYCLOAK_ADMIN_PORT:-8090\}:8080"/, 'o console abre só no PC');
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

