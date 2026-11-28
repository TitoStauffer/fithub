// Dados de exemplo do ambiente local inteiro (DEV-0181, infra/compose/docker-compose.app.yml). Só para desenvolvimento:
// mexe só no realm deste Keycloak local, nunca no arquivo versionado (que também vai para produção).
//
// Pelo admin do Keycloak: um client só local para o seed pedir token por senha, o retorno do Expo Go no app, o endereço
// da borda na web e as três contas de teste. Pelas APIs, como as pessoas fariam: o cadastro dos dois profissionais, o
// perfil e os dois vínculos do cliente, a ficha de treino e o plano alimentar publicados.
// Repetir não duplica nada: cada passo confere antes o que já existe.
const edge = (process.env.FITHUB_EDGE_URL ?? 'http://localhost:8000').replace(/\/$/, '');
const publicUrl = (process.env.FITHUB_PUBLIC_URL ?? edge).replace(/\/$/, '');
const password = process.env.FITHUB_SEED_PASSWORD;
if (!password) throw new Error('Defina FITHUB_SEED_PASSWORD.');

const SEED_CLIENT = 'fithub-dev-seed';
const ACCOUNTS = {
  personal: { email: 'personal@fithub.test', firstName: 'Paula', lastName: 'Personal' },
  nutritionist: { email: 'nutri@fithub.test', firstName: 'Nina', lastName: 'Nutricionista' },
  client: { email: 'cliente@fithub.test', firstName: 'Carlos', lastName: 'Cliente' },
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const log = (message) => console.log(`seed: ${message}`);

async function call(method, path, { token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) payload = new URLSearchParams(form);
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const response = await fetch(`${edge}${path}`, { method, headers, body: payload });
  const text = await response.text();
  let json;
  try {
    json = text ? JSON.parse(text) : undefined;
  } catch {
    json = undefined;
  }
  return { status: response.status, json, headers: response.headers };
}

/** Chama até receber um dos status esperados; os eventos entre os serviços chegam em alguns segundos. */
async function until(description, attempt, expected, tries = 30) {
  let last;
  for (let i = 0; i < tries; i++) {
    last = await attempt();
    if (expected.includes(last.status)) return last;
    await sleep(1000);
  }
  throw new Error(`${description}: status ${last.status} ${JSON.stringify(last.json)}`);
}

const claims = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));

// --- Keycloak, pelo admin do compose ---

async function adminToken() {
  const response = await until(
    'token do admin',
    () => call('POST', '/auth/realms/master/protocol/openid-connect/token', {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: process.env.KEYCLOAK_ADMIN ?? 'admin',
        password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
      },
    }),
    [200],
  );
  return response.json.access_token;
}

async function clientRepresentation(admin, clientId) {
  const { json } = await call('GET', `/auth/admin/realms/fithub/clients?clientId=${clientId}`, { token: admin });
  return json?.[0];
}

/** Acrescenta valores a uma lista do client, sem repetir; devolve se mudou. */
function addAll(list = [], values) {
  const missing = values.filter((value) => !list.includes(value));
  return [list.concat(missing), missing.length > 0];
}

async function configureClients(admin) {
  // Web servida pela borda: o endereço dela como retorno e origem, além dos do realm versionado.
  const web = await clientRepresentation(admin, 'fithub-web');
  const [webRedirects, webChanged] = addAll(web.redirectUris, [`${publicUrl}/*`]);
  const [webOrigins, originsChanged] = addAll(web.webOrigins, [publicUrl]);
  if (webChanged || originsChanged) {
    await call('PUT', `/auth/admin/realms/fithub/clients/${web.id}`, {
      token: admin,
      body: { ...web, redirectUris: webRedirects, webOrigins },
    });
    log(`fithub-web aceita ${publicUrl}`);
  }

  // Expo Go: volta do login por exp://<IP do PC>:8081/--/... O curinga vale só neste realm local; o realm versionado
  // continua só com fithub://auth (realm.test.mjs).
  const mobile = await clientRepresentation(admin, 'fithub-mobile');
  const [mobileRedirects, mobileChanged] = addAll(mobile.redirectUris, ['exp://*']);
  if (mobileChanged) {
    const logout = mobile.attributes?.['post.logout.redirect.uris'] ?? '';
    const attributes = { ...mobile.attributes, 'post.logout.redirect.uris': logout.includes('exp://*') ? logout : `${logout}##exp://*` };
    await call('PUT', `/auth/admin/realms/fithub/clients/${mobile.id}`, {
      token: admin,
      body: { ...mobile, redirectUris: mobileRedirects, attributes },
    });
    log('fithub-mobile aceita o retorno do Expo Go (exp://)');
  }

  // Client só deste realm local, para o seed pedir o token das contas de teste por senha.
  if (!(await clientRepresentation(admin, SEED_CLIENT))) {
    await call('POST', '/auth/admin/realms/fithub/clients', {
      token: admin,
      body: {
        clientId: SEED_CLIENT,
        enabled: true,
        publicClient: true,
        standardFlowEnabled: false,
        directAccessGrantsEnabled: true,
        description: 'Só do ambiente local (infra/dev-seed): token por senha para o seed.',
      },
    });
    log(`client ${SEED_CLIENT} criado`);
  }
}

async function ensureAccount(admin, { email, firstName, lastName }) {
  const search = await call('GET', `/auth/admin/realms/fithub/users?email=${encodeURIComponent(email)}&exact=true`, { token: admin });
  let id = search.json?.[0]?.id;
  if (!id) {
    const created = await call('POST', '/auth/admin/realms/fithub/users', {
      token: admin,
      body: { username: email, email, firstName, lastName, enabled: true, emailVerified: true },
    });
    id = created.headers.get('location')?.split('/').pop();
    if (created.status !== 201 || !id) throw new Error(`conta ${email}: status ${created.status}`);
    log(`conta ${email} criada`);
  }
  // A senha volta à de teste a cada rodada.
  await call('PUT', `/auth/admin/realms/fithub/users/${id}/reset-password`, {
    token: admin,
    body: { type: 'password', value: password, temporary: false },
  });
  return id;
}

async function login(email) {
  const response = await until(
    `login de ${email}`,
    () => call('POST', '/auth/realms/fithub/protocol/openid-connect/token', {
      form: { grant_type: 'password', client_id: SEED_CLIENT, username: email, password, scope: 'openid' },
    }),
    [200],
  );
  return response.json.access_token;
}

/** Token com o papel de profissional, que o account-service atribui depois do cadastro. */
async function loginWithRole(email, role) {
  for (let i = 0; i < 60; i++) {
    const token = await login(email);
    if (claims(token).realm_access?.roles?.includes(role)) return token;
    await sleep(1000);
  }
  throw new Error(`${email} não recebeu o papel ${role}`);
}

// --- Pelas APIs ---

async function registerProfessional(email, body, role) {
  const token = await login(email);
  await until(`cadastro de ${email}`, () => call('POST', '/api/account/professionals/me', { token, body }), [200, 201]);
  return loginWithRole(email, role);
}

async function inviteCode(token) {
  const response = await until('código de convite', () => call('GET', '/api/client/professionals/me/invite-code', { token }), [200]);
  return response.json.code;
}

async function linkClient(clientToken, professionalToken) {
  const professionalId = claims(professionalToken).sub;
  const links = await call('GET', '/api/client/links', { token: clientToken });
  if (links.json?.some((link) => link.professionalId === professionalId && link.status === 'ACTIVE')) return;
  const code = await inviteCode(professionalToken);
  await until(
    'aceite do vínculo',
    () => call('POST', '/api/client/links', { token: clientToken, body: { code, consent: true, termsVersion: 'v2' } }),
    [201],
  );
  log(`cliente vinculado a ${claims(professionalToken).email}`);
}

async function publishWorkoutPlan(personal, clientId) {
  const plans = await until('fichas do cliente', () => call('GET', `/api/training/clients/${clientId}/plans`, { token: personal }), [200]);
  const list = Array.isArray(plans.json) ? plans.json : (plans.json?.items ?? []);
  if (list.some((plan) => plan.status === 'PUBLISHED')) return;
  const name = 'Supino reto (exemplo)';
  const found = await call('GET', `/api/training/exercises?name=${encodeURIComponent(name)}`, { token: personal });
  const existing = (Array.isArray(found.json) ? found.json : (found.json?.items ?? [])).find((exercise) => exercise.name === name);
  const exerciseId =
    existing?.id ??
    (await until('exercício', () => call('POST', '/api/training/exercises', {
      token: personal,
      body: { name, muscleGroups: ['CHEST'], equipment: 'BARBELL' },
    }), [201])).json.id;
  const today = new Date().toISOString().slice(0, 10);
  const plan = await until('ficha', () => call('POST', `/api/training/clients/${clientId}/plans`, {
    token: personal,
    body: {
      name: 'Ficha de exemplo',
      startsOn: today,
      validityWeeks: 8,
      divisions: [{ name: 'Peito', items: [{ exerciseId, sets: 3, repsMin: 8, repsMax: 12, loadKg: 20, restSeconds: 60 }] }],
    },
  }), [201]);
  await until('publicação da ficha', () => call('POST', `/api/training/plans/${plan.json.id}/publish`, { token: personal }), [200]);
  log('ficha de treino publicada');
}

async function publishMealPlan(nutritionist, clientId) {
  const plans = await until('planos do cliente', () => call('GET', `/api/nutrition/clients/${clientId}/meal-plans`, { token: nutritionist }), [200]);
  const list = Array.isArray(plans.json) ? plans.json : (plans.json?.items ?? []);
  if (list.some((plan) => plan.status === 'PUBLISHED')) return;
  const name = 'Arroz branco (exemplo)';
  const found = await call('GET', `/api/nutrition/foods?name=${encodeURIComponent(name)}`, { token: nutritionist });
  const existing = (found.json?.items ?? []).find((food) => food.name === name);
  const foodId =
    existing?.id ??
    (await until('alimento', () => call('POST', '/api/nutrition/foods', {
      token: nutritionist,
      body: { name, baseUnit: 'G', energyKcal: 128, proteinG: 2.5, carbsG: 28.1, fatG: 0.2, fiberG: 1.6, vegan: true },
    }), [201])).json.id;
  const today = new Date().toISOString().slice(0, 10);
  const plan = await until('plano alimentar', () => call('POST', `/api/nutrition/clients/${clientId}/meal-plans`, {
    token: nutritionist,
    body: { name: 'Plano de exemplo', startsOn: today, meals: [{ name: 'Almoço', suggestedTime: '12:30', items: [{ foodId, amount: 150 }] }] },
  }), [201]);
  await until('publicação do plano', () => call('POST', `/api/nutrition/meal-plans/${plan.json.id}/publish`, { token: nutritionist }), [200]);
  log('plano alimentar publicado');
}

const admin = await adminToken();
await configureClients(admin);
for (const account of Object.values(ACCOUNTS)) await ensureAccount(admin, account);

const personal = await registerProfessional(
  ACCOUNTS.personal.email,
  { type: 'PERSONAL', registrationNumber: '123456', registrationCategory: 'G', registrationState: 'SP' },
  'personal',
);
const nutritionist = await registerProfessional(
  ACCOUNTS.nutritionist.email,
  { type: 'NUTRITIONIST', registrationNumber: '12345', registrationState: 'SP' },
  'nutritionist',
);

const client = await login(ACCOUNTS.client.email);
await until('perfil do cliente', () => call('PUT', '/api/client/clients/me/profile', {
  token: client,
  body: { name: 'Carlos Cliente', birthDate: '1995-05-10', gender: 'MALE', heightCm: 178, goal: 'HYPERTROPHY' },
}), [200, 201]);
await linkClient(client, personal);
await linkClient(client, nutritionist);

const clientId = claims(client).sub;
await publishWorkoutPlan(personal, clientId);
await publishMealPlan(nutritionist, clientId);

log(`pronto. Contas: ${Object.values(ACCOUNTS).map((account) => account.email).join(', ')}, senha FITHUB_SEED_PASSWORD.`);
