// Guardas do realm fithub versionado (DEV-0024). Confere o arquivo que o Keycloak importa; o
// comportamento no Keycloak de pé é conferido por check-live-realm.mjs no job Local Infrastructure.
//
//   node --test infra/keycloak/realm.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const realm = JSON.parse(readFileSync(new URL('./realm-fithub.json', import.meta.url), 'utf8'));
const client = (clientId) => realm.clients.find((candidate) => candidate.clientId === clientId);
const APP_CLIENTS = ['fithub-web', 'fithub-mobile'];
const REALM_ROLES = ['client', 'personal', 'nutritionist', 'admin'];

test('o realm se chama fithub e está habilitado', () => {
  assert.equal(realm.realm, 'fithub');
  assert.equal(realm.enabled, true);
});

test('só os clients do web e do mobile são declarados', () => {
  assert.deepEqual(realm.clients.map(({ clientId }) => clientId).sort(), [...APP_CLIENTS].sort());
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

test('token de acesso curto e TLS exigido fora da máquina local', () => {
  assert.ok(realm.accessTokenLifespan <= 300, 'access token acima de 5 minutos');
  assert.notEqual(realm.sslRequired, 'none');
});

test('o arquivo não traz usuário, credencial, secret nem senha de SMTP', () => {
  assert.equal(realm.users, undefined, 'usuário versionado no realm');
  assert.equal(realm.identityProviders, undefined, 'provedor de identidade entra com a DEV-0025');
  assert.equal(realm.smtpServer.host, 'mailpit');
  assert.equal(realm.smtpServer.auth, 'false');
  assert.equal(realm.smtpServer.password, undefined);
  assert.equal(realm.smtpServer.user, undefined);
  // Rede de segurança para qualquer nível do arquivo: nenhuma chave com cara de segredo.
  const secretKeys = [];
  const visit = (value, path) => {
    if (Array.isArray(value)) value.forEach((item, index) => visit(item, `${path}[${index}]`));
    else if (value && typeof value === 'object') {
      for (const [key, inner] of Object.entries(value)) {
        if (/secret|password$|credentials|privateKey/i.test(key) && key !== 'passwordPolicy') secretKeys.push(`${path}.${key}`);
        visit(inner, `${path}.${key}`);
      }
    }
  };
  visit(realm, 'realm');
  assert.deepEqual(secretKeys, []);
});
