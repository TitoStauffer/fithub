// Guardas do realm fithub versionado (DEV-0024). Confere o arquivo que o Keycloak importa; o
// comportamento no Keycloak de pé é conferido por check-live-realm.mjs no job Local Infrastructure.
//
//   node --test infra/keycloak/realm.test.mjs
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import test from 'node:test';

const raw = readFileSync(new URL('./realm-fithub.json', import.meta.url), 'utf8');
// Variáveis com padrão (${NOME:padrão}), resolvidas como o import do Keycloak faz sem a variável: o realm do
// desenvolvimento. Em produção, o compose passa os endereços e o SMTP de produção (DEV-0176).
const DEFAULTED = /\$\{([A-Z_]+):([^}]*)\}/g;
const realm = JSON.parse(raw.replace(DEFAULTED, (_, name, fallback) => fallback));
const rawRealm = JSON.parse(raw);
const client = (clientId) => realm.clients.find((candidate) => candidate.clientId === clientId);
const APP_CLIENTS = ['fithub-web', 'fithub-mobile'];
// Client confidencial do account-service, só para atribuir o papel de profissional (DEV-0029, ADR-008).
const SERVICE_CLIENT = 'fithub-account-service';
const SERVICE_ACCOUNT = `service-account-${SERVICE_CLIENT}`;
// Só ler o realm (o papel que atribui); o resto vem das permissões finas abaixo (DEV-0147). Com manage-users, o client
// atribuiria qualquer papel de realm, inclusive admin.
const SERVICE_ADMIN_ROLES = ['view-realm'];
// Client que o Keycloak usa para guardar as permissões finas de administração (admin permissions v2).
const PERMISSIONS_CLIENT = 'admin-permissions';
const REALM_ROLES = ['client', 'personal', 'nutritionist', 'admin'];

test('o realm se chama fithub e está habilitado', () => {
  assert.equal(realm.realm, 'fithub');
  assert.equal(realm.enabled, true);
});

test('só os clients do web, do mobile e do account-service são declarados', () => {
  assert.deepEqual(
    realm.clients.map(({ clientId }) => clientId).sort(),
    [...APP_CLIENTS, SERVICE_CLIENT, PERMISSIONS_CLIENT].sort(),
  );
});

test(`${SERVICE_CLIENT}: confidencial, só client credentials, secret por variável de ambiente`, () => {
  const candidate = client(SERVICE_CLIENT);
  assert.equal(candidate.enabled, true);
  assert.equal(candidate.publicClient, false);
  assert.equal(candidate.serviceAccountsEnabled, true);
  assert.equal(candidate.standardFlowEnabled, false, 'o client não faz login de pessoa');
  assert.equal(candidate.implicitFlowEnabled, false);
  assert.equal(candidate.directAccessGrantsEnabled, false);
  assert.deepEqual(candidate.redirectUris, []);
  assert.deepEqual(candidate.webOrigins, []);
  // O valor real fica fora do repositório: o import do Keycloak troca o marcador pela variável.
  assert.equal(candidate.secret, '${FITHUB_ACCOUNT_SERVICE_SECRET}');
  // Sem full scope, o token só leva os papéis mapeados abaixo; kc-admin-api é exigido pela API de admin.
  assert.equal(candidate.fullScopeAllowed, false);
  assert.deepEqual(candidate.defaultClientScopes, ['kc-admin-api']);
});

test(`${SERVICE_CLIENT} só mapeia personal e nutritionist, pelas permissões finas (DEV-0147)`, () => {
  assert.equal(realm.adminPermissionsEnabled, true);
  const settings = client(PERMISSIONS_CLIENT).authorizationSettings;
  const roleId = (name) => realm.roles.realm.find((role) => role.name === name).id;
  const config = (name) => settings.policies.find((policy) => policy.name === name)?.config;
  // Uma política: o próprio client do account-service, e só ele.
  assert.deepEqual(
    settings.policies.filter((policy) => policy.type !== 'scope').map(({ name, type, logic, config: c }) => [name, type, logic, c]),
    [['account-service', 'client', 'POSITIVE', { clients: JSON.stringify([SERVICE_CLIENT]) }]],
  );
  assert.deepEqual(
    settings.policies.filter((policy) => policy.type === 'scope').map(({ name }) => name).sort(),
    ['account-service-professional-roles', 'account-service-users'],
  );
  // Nos usuários: ler, apagar (exclusão de conta, DEV-0161) e mapear papéis.
  assert.deepEqual(JSON.parse(config('account-service-users').scopes).sort(), ['manage', 'map-roles', 'view']);
  assert.deepEqual(JSON.parse(config('account-service-users').resources), ['Users']);
  // Nos papéis: só mapear, e só personal e nutritionist.
  assert.deepEqual(JSON.parse(config('account-service-professional-roles').scopes), ['map-role']);
  assert.deepEqual(
    JSON.parse(config('account-service-professional-roles').resources).sort(),
    [roleId('personal'), roleId('nutritionist')].sort(),
  );
  assert.ok(roleId('personal') && roleId('nutritionist'), 'os papéis têm id fixo, citado na permissão');
});

test(`a conta de serviço do ${SERVICE_CLIENT} tem só ${SERVICE_ADMIN_ROLES.join(' e ')}`, () => {
  assert.deepEqual(realm.users, [
    {
      username: SERVICE_ACCOUNT,
      enabled: true,
      serviceAccountClientId: SERVICE_CLIENT,
      realmRoles: [],
      clientRoles: { 'realm-management': SERVICE_ADMIN_ROLES },
    },
  ]);
  assert.deepEqual(realm.clientScopeMappings, {
    'realm-management': [{ client: SERVICE_CLIENT, roles: SERVICE_ADMIN_ROLES }],
  });
  assert.equal(realm.scopeMappings, undefined, 'nenhum papel de realm entra no token do client');
});

for (const clientId of APP_CLIENTS) {
  test(`${clientId}: público, só Authorization Code, PKCE S256 obrigatório`, () => {
    const candidate = client(clientId);
    assert.equal(candidate.enabled, true);
    assert.equal(candidate.protocol, 'openid-connect');
    assert.equal(candidate.publicClient, true);
    assert.equal(candidate.standardFlowEnabled, true);
    assert.equal(candidate.implicitFlowEnabled, false, 'fluxo implícito entrega token na URL');
    assert.equal(candidate.directAccessGrantsEnabled, false, 'login por senha direta tira o Keycloak do caminho');
    assert.equal(candidate.serviceAccountsEnabled, false);
    assert.equal(candidate.attributes['pkce.code.challenge.method'], 'S256');
    assert.equal(candidate.secret, undefined, 'client público não tem secret');
  });
}

test('redirect URIs e origens não têm curinga de host nem aceitam qualquer destino', () => {
  // O curinga só pode vir no fim do caminho, depois de um host fixo: "http://localhost:5173/*".
  const fixedHost = /^(https?:\/\/[a-z0-9.-]+(:\d+)?\/([^*]*\*?)?|fithub:\/\/[a-z0-9/-]+)$/;
  for (const clientId of APP_CLIENTS) {
    const candidate = client(clientId);
    const logout = candidate.attributes['post.logout.redirect.uris'].split('##');
    assert.ok(candidate.redirectUris.length > 0, `${clientId} sem redirect URI`);
    for (const uri of [...candidate.redirectUris, ...logout]) {
      assert.match(uri, fixedHost, `${clientId}: redirect URI aberta demais: ${uri}`);
    }
    for (const origin of candidate.webOrigins) {
      assert.match(origin, /^https?:\/\/[a-z0-9.-]+(:\d+)?$/, `${clientId}: origem aberta demais: ${origin}`);
    }
  }
  assert.deepEqual(client('fithub-mobile').redirectUris, ['fithub://auth']);
});

// Code Review do PR #190 (LOW): recusar curinga não basta, porque um host fixo de terceiro passaria.
// Os destinos de cada client são fixados por valor; mudar um deles é decisão revisada, com este teste.
// DEV-0176: o endereço da web vem de FITHUB_WEB_URL, com o do desenvolvimento como padrão. Nenhum outro campo dos
// clients aceita variável, e o curinga continua só no fim do caminho, fora da variável.
test('o endereço da web vem só de FITHUB_WEB_URL, e o SMTP do realm vem do ambiente', () => {
  const url = '${FITHUB_WEB_URL:http://localhost:5173}';
  const web = rawRealm.clients.find((candidate) => candidate.clientId === 'fithub-web');
  assert.equal(web.rootUrl, url);
  assert.deepEqual(web.redirectUris, [`${url}/*`]);
  assert.deepEqual(web.webOrigins, [url]);
  assert.equal(web.attributes['post.logout.redirect.uris'], `${url}/*`);
  const variables = [...raw.matchAll(/\$\{([A-Z_]+)(:[^}]*)?\}/g)].map((match) => match[1]);
  assert.deepEqual([...new Set(variables)].sort(), [
    'FITHUB_ACCOUNT_SERVICE_SECRET',
    'FITHUB_SMTP_AUTH',
    'FITHUB_SMTP_FROM',
    'FITHUB_SMTP_HOST',
    'FITHUB_SMTP_PASSWORD',
    'FITHUB_SMTP_PORT',
    'FITHUB_SMTP_STARTTLS',
    'FITHUB_SMTP_USER',
    'FITHUB_WEB_URL',
  ]);
  // O desenvolvimento continua no Mailpit, sem autenticação.
  assert.equal(realm.smtpServer.host, 'mailpit');
  assert.equal(realm.smtpServer.auth, 'false');
});

test('os destinos do web e do mobile são exatamente os esperados', () => {
  const web = client('fithub-web');
  assert.deepEqual(web.redirectUris, ['http://localhost:5173/*']);
  assert.deepEqual(web.webOrigins, ['http://localhost:5173']);
  assert.equal(web.attributes['post.logout.redirect.uris'], 'http://localhost:5173/*');
  assert.equal(web.rootUrl, 'http://localhost:5173');
  const mobile = client('fithub-mobile');
  assert.deepEqual(mobile.webOrigins, []);
  assert.equal(mobile.attributes['post.logout.redirect.uris'], 'fithub://auth');
});

test('os quatro papéis existem e conta nova recebe só client', () => {
  const roles = new Map(realm.roles.realm.map((role) => [role.name, role]));
  for (const name of REALM_ROLES) assert.ok(roles.has(name), `papel ${name} ausente`);
  assert.equal(realm.defaultRole.name, 'default-roles-fithub');
  const defaults = roles.get('default-roles-fithub').composites;
  // offline_access e uma_authorization são os padrões do Keycloak; nenhum papel de profissional ou admin.
  assert.deepEqual([...defaults.realm].sort(), ['client', 'offline_access', 'uma_authorization']);
  assert.deepEqual(defaults.client, { account: ['view-profile', 'manage-account'] });
  // Papel do FitHub não é composto: ter client não pode implicar outro papel.
  for (const name of REALM_ROLES) assert.equal(roles.get(name).composites, undefined, `papel ${name} não pode ser composto`);
});

test('cadastro por e-mail, verificação de e-mail e recuperação de senha habilitados', () => {
  assert.equal(realm.registrationAllowed, true);
  assert.equal(realm.registrationEmailAsUsername, true);
  assert.equal(realm.loginWithEmailAllowed, true);
  assert.equal(realm.duplicateEmailsAllowed, false);
  assert.equal(realm.verifyEmail, true);
  assert.equal(realm.resetPasswordAllowed, true);
});

test('política de senha e proteção contra força bruta', () => {
  const policies = realm.passwordPolicy.split(' and ');
  const length = policies.map((policy) => /^length\((\d+)\)$/.exec(policy)).find(Boolean);
  assert.ok(length && Number(length[1]) >= 8, 'senha com menos de 8 caracteres');
  assert.ok(policies.includes('notUsername'));
  assert.ok(policies.includes('notEmail'));
  assert.equal(realm.bruteForceProtected, true);
  assert.equal(realm.permanentLockout, false, 'bloqueio permanente vira negação de serviço contra a conta');
  assert.ok(realm.failureFactor > 0 && realm.failureFactor <= 10);
});

test('telas e e-mails em pt-BR', () => {
  assert.equal(realm.internationalizationEnabled, true);
  assert.equal(realm.defaultLocale, 'pt-BR');
  assert.ok(realm.supportedLocales.includes('pt-BR'));
});

// --- tema (DEV-0026) ---

const THEME = new URL('./themes/fithub/', import.meta.url);
const themeFiles = (directory = THEME) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const url = new URL(entry.isDirectory() ? `${entry.name}/` : entry.name, directory);
  return entry.isDirectory() ? themeFiles(url) : [url];
});

test('o realm usa o tema fithub nas telas de login e nos e-mails, e o tema existe', () => {
  assert.equal(realm.loginTheme, 'fithub');
  assert.equal(realm.emailTheme, 'fithub');
  assert.ok(existsSync(new URL('login/theme.properties', THEME)));
  assert.ok(existsSync(new URL('email/theme.properties', THEME)));
});

test('o tema só muda aparência e textos: sem script, sem página de login própria e sem recurso externo', () => {
  const files = themeFiles();
  assert.ok(files.length > 0);
  for (const url of files) {
    const path = url.pathname.slice(url.pathname.indexOf('/themes/fithub/'));
    assert.doesNotMatch(path, /\.(js|mjs)$/, `script no tema: ${path}`);
    // Sobrescrever uma página do login tiraria as correções das telas do Keycloak; só a moldura do e-mail muda.
    if (path.endsWith('.ftl')) assert.equal(path, '/themes/fithub/email/html/template.ftl', `página sobrescrita: ${path}`);
    const content = readFileSync(url, 'utf8');
    assert.doesNotMatch(content, /<script|javascript:|\bon[a-z]+\s*=/i, `script em ${path}`);
    assert.doesNotMatch(content, /https?:\/\/|\/\/[a-z0-9.-]+\.[a-z]{2,}|@import|<img|<link|<iframe/i, `recurso externo em ${path}`);
    assert.doesNotMatch(content, /url\(/i, `url() em ${path}`);
  }
});

test('o tema de login herda as telas do keycloak.v2 e só acrescenta o CSS do FitHub', () => {
  const properties = readFileSync(new URL('login/theme.properties', THEME), 'utf8');
  assert.match(properties, /^parent=keycloak\.v2$/m);
  assert.match(properties, /^styles=css\/styles\.css css\/fithub\.css$/m);
  assert.doesNotMatch(properties, /^scripts=/m);
});

// Login e recuperação respondem igual com ou sem conta. No cadastro só o texto é neutro: o erro em si
// ainda aparece só para e-mail já cadastrado (limite do fluxo do Keycloak).
test('login e recuperação não revelam se o e-mail tem conta, e o cadastro usa texto neutro', () => {
  const messages = Object.fromEntries(readFileSync(new URL('login/messages/messages_pt_BR.properties', THEME), 'utf8')
    .split(/\r?\n/).filter((line) => line && !line.startsWith('#')).map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
  assert.equal(messages.invalidUserMessage, 'E-mail ou senha inválidos.');
  assert.equal(messages.emailExistsMessage, messages.usernameExistsMessage);
  for (const key of ['emailExistsMessage', 'usernameExistsMessage', 'emailSentMessage']) {
    assert.doesNotMatch(messages[key], /já existe|já está|não existe|não encontrad/i, `${key} revela se a conta existe`);
  }
  assert.match(messages.emailSentMessage, /^Se houver uma conta/);
});

test('token de acesso curto e TLS exigido fora da máquina local', () => {
  assert.ok(realm.accessTokenLifespan <= 300, 'access token acima de 5 minutos');
  assert.notEqual(realm.sslRequired, 'none');
});

test('o arquivo não traz usuário de pessoa, credencial, secret nem senha de SMTP', () => {
  // O único usuário é a conta de serviço do account-service, sem credencial (teste acima).
  for (const user of realm.users) {
    assert.equal(user.username, SERVICE_ACCOUNT, `usuário versionado no realm: ${user.username}`);
    assert.equal(user.credentials, undefined);
  }
  assert.equal(realm.identityProviders, undefined, 'provedor de identidade entra com a DEV-0025');
  assert.equal(realm.smtpServer.host, 'mailpit');
  assert.equal(realm.smtpServer.auth, 'false');
  // O usuário e a senha do SMTP de produção vêm só do ambiente (DEV-0176): no arquivo, a variável sem valor.
  assert.equal(rawRealm.smtpServer.password, '${FITHUB_SMTP_PASSWORD:}');
  assert.equal(rawRealm.smtpServer.user, '${FITHUB_SMTP_USER:}');
  assert.equal(realm.smtpServer.password, '');
  assert.equal(realm.smtpServer.user, '');
  // Rede de segurança para qualquer nível do arquivo: nenhuma chave com cara de segredo.
  const secretKeys = [];
  const visit = (value, path) => {
    if (Array.isArray(value)) value.forEach((item, index) => visit(item, `${path}[${index}]`));
    else if (value && typeof value === 'object') {
      for (const [key, inner] of Object.entries(value)) {
        // Só a variável sem valor padrão (${NOME} ou ${NOME:}): um segredo não entra disfarçado de padrão.
        const placeholder = typeof inner === 'string' && /^\$\{[A-Z0-9_]+:?\}$/.test(inner);
        if (/secret|password$|credentials|privateKey/i.test(key) && key !== 'passwordPolicy' && !placeholder) {
          secretKeys.push(`${path}.${key}`);
        }
        visit(inner, `${path}.${key}`);
      }
    }
  };
  visit(rawRealm, 'realm');
  assert.deepEqual(secretKeys, []);
});
