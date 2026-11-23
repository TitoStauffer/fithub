#!/usr/bin/env node
// Confere no Keycloak de pé que o realm fithub foi importado e que os clients recusam o que não é
// Authorization Code + PKCE (DEV-0024). Roda no job Local Infrastructure, depois do compose subir.
//
//   node infra/keycloak/check-live-realm.mjs [http://localhost:8080]
//
// Não usa credencial de admin nem cria nada. A única credencial é a do client do account-service
// (FITHUB_ACCOUNT_SERVICE_SECRET, com o padrão de desenvolvimento do compose), para conferir que ele
// faz só o que precisa (DEV-0029, ADR-008).
const base = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '');
const realm = `${base}/realms/fithub`;
const CHALLENGE = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';
const CLIENTS = [
  { clientId: 'fithub-web', redirectUri: 'http://localhost:5173/callback' },
  { clientId: 'fithub-mobile', redirectUri: 'fithub://auth' },
];
const failures = [];
const check = (condition, message) => {
  console.log(`${condition ? 'ok  ' : 'FALHA'} ${message}`);
  if (!condition) failures.push(message);
};

const authorize = (clientId, redirectUri, extra) => {
  const query = new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri, scope: 'openid', state: 'check', ...extra });
  return fetch(`${realm}/protocol/openid-connect/auth?${query}`, { redirect: 'manual' });
};
/** Erro OAuth devolvido ao redirect URI, na query (code) ou no fragmento (token). */
const redirectError = (response) => {
  const location = response.headers.get('location') ?? '';
  return new URLSearchParams(location.replace(/^[^?#]*[?#]/, '')).get('error');
};

const discovery = await fetch(`${realm}/.well-known/openid-configuration`);
check(discovery.status === 200, 'o realm fithub responde ao discovery OIDC');
if (discovery.status === 200) {
  const configuration = await discovery.json();
  check(configuration.issuer === realm, `issuer é ${realm} (veio ${configuration.issuer})`);
}

for (const { clientId, redirectUri } of CLIENTS) {
  const withPkce = await authorize(clientId, redirectUri, { response_type: 'code', code_challenge: CHALLENGE, code_challenge_method: 'S256' });
  check(withPkce.status === 200, `${clientId}: Authorization Code com PKCE S256 abre o login`);

  // Tema do FitHub (DEV-0026): a tela de login carrega o CSS do tema, e o Keycloak consegue servi-lo.
  const page = withPkce.status === 200 ? await withPkce.text() : '';
  const stylesheet = /href="([^"]*\/login\/fithub\/css\/fithub\.css)"/.exec(page)?.[1];
  check(Boolean(stylesheet), `${clientId}: a tela de login usa o tema fithub`);
  if (stylesheet) {
    const css = await fetch(new URL(stylesheet, base));
    check(css.status === 200, `${clientId}: o CSS do tema é servido pelo Keycloak`);
  }

  const withoutPkce = await authorize(clientId, redirectUri, { response_type: 'code' });
  check(withoutPkce.status === 302 && redirectError(withoutPkce) === 'invalid_request', `${clientId}: sem PKCE é recusado`);

  const plain = await authorize(clientId, redirectUri, { response_type: 'code', code_challenge: CHALLENGE, code_challenge_method: 'plain' });
  check(plain.status === 302 && redirectError(plain) === 'invalid_request', `${clientId}: PKCE plain é recusado`);

  const implicit = await authorize(clientId, redirectUri, { response_type: 'token' });
  check(implicit.status === 302 && redirectError(implicit) === 'unauthorized_client', `${clientId}: fluxo implícito é recusado`);

  const elsewhere = await authorize(clientId, 'https://evil.example/callback', { response_type: 'code', code_challenge: CHALLENGE, code_challenge_method: 'S256' });
  check(elsewhere.status === 400 && !elsewhere.headers.get('location'), `${clientId}: redirect URI fora da lista é recusada sem redirecionar`);

  const password = await fetch(`${realm}/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({ grant_type: 'password', client_id: clientId, username: 'ninguem@fithub.local', password: 'nao-importa' }),
  });
  const body = await password.json().catch(() => ({}));
  check(password.status >= 400 && body.error === 'unauthorized_client', `${clientId}: login por senha direta é recusado`);
}

// Client do account-service (DEV-0029): consegue o token e ler o papel que atribui, mas não enxerga
// os clients nem altera o realm.
const serviceToken = await fetch(`${realm}/protocol/openid-connect/token`, {
  method: 'POST',
  body: new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: 'fithub-account-service',
    client_secret: process.env.FITHUB_ACCOUNT_SERVICE_SECRET ?? 'fithub-dev-account-service',
  }),
});
const { access_token: serviceAccess } = await serviceToken.json().catch(() => ({}));
check(serviceToken.status === 200 && Boolean(serviceAccess), 'fithub-account-service obtém token por client credentials');
if (serviceAccess) {
  const admin = (path, init = {}) =>
    fetch(`${base}/admin/realms/fithub${path}`, { ...init, headers: { Authorization: `Bearer ${serviceAccess}`, 'Content-Type': 'application/json' } });
  const role = await admin('/roles/personal');
  check(role.status === 200, 'fithub-account-service lê o papel personal');
  const clients = await admin('/clients');
  check(clients.status === 403, `fithub-account-service não lista os clients (${clients.status})`);
  const realmChange = await admin('', { method: 'PUT', body: JSON.stringify({ registrationAllowed: false }) });
  check(realmChange.status === 403, `fithub-account-service não altera o realm (${realmChange.status})`);
}

if (failures.length > 0) {
  console.error(`\n${failures.length} verificação(ões) do realm falharam.`);
  process.exit(1);
}
console.log('\nRealm fithub importado e clients restritos a Authorization Code + PKCE.');
