#!/usr/bin/env node
// Confere no Keycloak de pé que o realm fithub foi importado e que os clients recusam o que não é
// Authorization Code + PKCE (DEV-0024). Roda no job Local Infrastructure, depois do compose subir.
//
//   node infra/keycloak/check-live-realm.mjs [http://localhost:8080]
//
// Só lê endpoints públicos do realm: não usa credencial de admin nem cria nada.
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

if (failures.length > 0) {
  console.error(`\n${failures.length} verificação(ões) do realm falharam.`);
  process.exit(1);
}
console.log('\nRealm fithub importado e clients restritos a Authorization Code + PKCE.');
