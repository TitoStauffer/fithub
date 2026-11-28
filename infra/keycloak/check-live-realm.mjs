#!/usr/bin/env node
// Confere no Keycloak de pé que o realm fithub foi importado e que os clients recusam o que não é
// Authorization Code + PKCE (DEV-0024), além do cadastro em duas etapas (DEV-0145). Roda no job Local Infrastructure,
// depois do compose subir.
//
//   node infra/keycloak/check-live-realm.mjs [http://localhost:8080] [http://localhost:8025]
//
// O segundo endereço é a API do Mailpit do compose, onde a checagem lê o e-mail de verificação do cadastro (DEV-0145).
//
// Usa a credencial do client do account-service (FITHUB_ACCOUNT_SERVICE_SECRET, com o padrão de desenvolvimento do
// compose) para conferir que ele faz só o que precisa (DEV-0029, ADR-008). Para provar as permissões finas (DEV-0147),
// cria com o admin do compose (KEYCLOAK_ADMIN/KEYCLOAK_ADMIN_PASSWORD, padrão admin) uma conta só para a checagem, que
// o próprio client apaga no fim. O cadastro em duas etapas (DEV-0145) também cria uma conta, que o admin apaga no fim.
const base = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '');
const mailpit = (process.argv[3] ?? 'http://localhost:8025').replace(/\/$/, '');
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

/** Token do admin do compose (só de desenvolvimento), pedido uma vez. Sem ele, devolve undefined. */
let adminToken;
async function asAdmin(path, init = {}) {
  if (adminToken === undefined) {
    const master = await fetch(`${base}/realms/master/protocol/openid-connect/token`, {
      method: 'POST',
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'admin-cli',
        username: process.env.KEYCLOAK_ADMIN ?? 'admin',
        password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
      }),
    });
    adminToken = (await master.json().catch(() => ({}))).access_token ?? '';
    check(Boolean(adminToken), 'o admin do compose obtém token para as contas da checagem');
  }
  if (!adminToken) return undefined;
  return fetch(`${base}/admin/realms/fithub${path}`, { ...init, headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' } });
}

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
  await checkProfessionalRolesOnly(admin);
}

/**
 * Permissões finas (DEV-0147): numa conta criada só para a checagem, o client mapeia personal, mas não admin nem client,
 * e apaga a conta (exclusão de conta, DEV-0161).
 */
async function checkProfessionalRolesOnly(service) {
  const username = `checagem-papeis-${Date.now()}`;
  const created = await asAdmin('/users', { method: 'POST', body: JSON.stringify({ username, email: `${username}@checagem.test`, enabled: true }) });
  if (!created) return;
  const userId = created.headers.get('location')?.split('/').pop();
  check(created.status === 201 && Boolean(userId), 'a conta da checagem foi criada');
  if (!userId) return;
  const roleOf = async (name) => (await asAdmin(`/roles/${name}`)).json();
  const map = async (name) =>
    (await service(`/users/${userId}/role-mappings/realm`, { method: 'POST', body: JSON.stringify([await roleOf(name)]) })).status;
  const personal = await map('personal');
  check(personal === 204, `fithub-account-service atribui personal (${personal})`);
  for (const name of ['admin', 'client']) {
    const status = await map(name);
    check(status === 403, `fithub-account-service não atribui ${name} (${status})`);
  }
  // A escalada mais grave: papel administrativo do próprio Keycloak (client realm-management) na conta.
  const [management] = await (await asAdmin('/clients?clientId=realm-management')).json();
  for (const name of ['realm-admin', 'manage-users']) {
    const role = await (await asAdmin(`/clients/${management.id}/roles/${name}`)).json();
    const status = (
      await service(`/users/${userId}/role-mappings/clients/${management.id}`, { method: 'POST', body: JSON.stringify([role]) })
    ).status;
    check(status === 403, `fithub-account-service não atribui ${name} do realm-management (${status})`);
  }
  const removed = await service(`/users/${userId}`, { method: 'DELETE' });
  check(removed.status === 204, `fithub-account-service apaga a conta (${removed.status})`);
  if (removed.status !== 204) await asAdmin(`/users/${userId}`, { method: 'DELETE' });
}

await checkTwoStepRegistration();

/**
 * Cadastro em duas etapas (DEV-0145, ENGENHARIA.md §4): com a verificação de e-mail ligada, a tela de cadastro não pede
 * senha, a conta nasce sem credencial e o e-mail de verificação sai pelo Mailpit com a moldura do tema fithub.
 */
async function checkTwoStepRegistration() {
  const settings = await asAdmin('');
  if (!settings) return;
  const { verifyEmail, emailTheme } = await settings.json();
  check(verifyEmail === true, `o realm de pé exige a verificação de e-mail (verifyEmail ${verifyEmail})`);
  check(emailTheme === 'fithub', `o realm de pé usa o tema de e-mail fithub (${emailTheme})`);

  const query = new URLSearchParams({
    client_id: 'fithub-web',
    redirect_uri: 'http://localhost:5173/callback',
    scope: 'openid',
    state: 'check',
    response_type: 'code',
    code_challenge: CHALLENGE,
    code_challenge_method: 'S256',
  });
  const form = await fetch(`${realm}/protocol/openid-connect/registrations?${query}`, { redirect: 'manual' });
  const page = form.status === 200 ? await form.text() : '';
  const fields = [...page.matchAll(/<input[^>]*\bname="([^"]+)"/g)].map((match) => match[1]);
  check(fields.includes('email') && fields.includes('firstName'), `a tela de cadastro pede e-mail e nome (${fields.join(', ')})`);
  check(!fields.some((name) => name.startsWith('password')), 'a tela de cadastro não pede senha');
  const action = /<form[^>]*id="kc-register-form"[^>]*action="([^"]+)"/.exec(page)?.[1]?.replaceAll('&amp;', '&');
  if (!action) return;

  const email = `checagem-cadastro-${Date.now()}@checagem.test`;
  const cookies = (response) => response.headers.getSetCookie().map((cookie) => cookie.split(';')[0]);
  const session = cookies(form);
  const submitted = await fetch(action, {
    method: 'POST',
    redirect: 'manual',
    headers: { cookie: session.join('; ') },
    body: new URLSearchParams({ email, firstName: 'Checagem', lastName: 'Cadastro' }),
  });
  const next = submitted.headers.get('location') ?? '';
  check(submitted.status === 302 && next.includes('execution=VERIFY_EMAIL'), 'o cadastro sem senha segue para a verificação de e-mail');
  // O Keycloak manda o e-mail de verificação quando a tela da verificação abre.
  if (next) await fetch(next, { redirect: 'manual', headers: { cookie: [...session, ...cookies(submitted)].join('; ') } });

  const [user] = await (await asAdmin(`/users?email=${encodeURIComponent(email)}&exact=true`)).json();
  check(Boolean(user), 'o cadastro criou a conta');
  if (user) {
    const credentials = await (await asAdmin(`/users/${user.id}/credentials`)).json();
    check(credentials.length === 0, `a conta nasce sem senha, antes de confirmar o e-mail (${credentials.length} credencial(is))`);
  }

  const mail = await waitForMail(email);
  check(Boolean(mail), 'o e-mail de verificação chegou ao Mailpit');
  if (mail) {
    const message = await (await fetch(`${mailpit}/api/v1/message/${mail.ID}`)).json();
    // O rodapé vem da moldura do tema (template.ftl), com o texto do tema (messages_pt_BR.properties).
    check(
      (message.HTML ?? '').includes('Você recebeu este e-mail porque alguém usou este endereço no FitHub'),
      'o e-mail de verificação sai com a moldura do tema fithub',
    );
    await fetch(`${mailpit}/api/v1/messages`, { method: 'DELETE', body: JSON.stringify({ IDs: [mail.ID] }) });
  }
  if (user) {
    const removed = await asAdmin(`/users/${user.id}`, { method: 'DELETE' });
    check(removed.status === 204, `a conta do cadastro da checagem foi apagada (${removed.status})`);
  }
}

/** Primeira mensagem do Mailpit para o endereço, esperando até 10 s. */
async function waitForMail(address) {
  const search = `${mailpit}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`;
  for (let attempt = 0; attempt < 20; attempt++) {
    const found = await (await fetch(search)).json().catch(() => ({}));
    if (found.messages?.length) return found.messages[0];
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return undefined;
}

if (failures.length > 0) {
  console.error(`\n${failures.length} verificação(ões) do realm falharam.`);
  process.exit(1);
}
console.log('\nRealm fithub importado, clients restritos a Authorization Code + PKCE e cadastro em duas etapas.');
