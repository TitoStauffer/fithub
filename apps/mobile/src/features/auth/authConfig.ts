import { runtimeRedirectUri } from './runtimeRedirect';

const DEFAULT_KEYCLOAK_URL = 'http://localhost:8080';
const DEFAULT_CLIENT_API_URL = 'http://localhost:8082';
const DEFAULT_TRAINING_API_URL = 'http://localhost:8083';
const DEFAULT_NUTRITION_API_URL = 'http://localhost:8084';

/** A única redirect URI que o realm versionado aceita para o app (ADR-007). */
export const PRODUCTION_REDIRECT_URI = 'fithub://auth';

/** Porta da borda do ambiente local inteiro (infra/compose/docker-compose.app.yml). */
const LOCAL_EDGE_PORT = 8000;

function setting(value: string | undefined, fallback: string): string {
    return value && value.length > 0 ? value : fallback;
}

/**
 * O retorno do login (DEV-0181). Só em desenvolvimento e no Expo Go (`exp://`, que só o realm local aceita) vale o
 * retorno do Expo; o build do app usa sempre `fithub://auth`.
 */
export function redirectUriFor(dev: boolean, runtimeRedirect: string): string {
    return dev && runtimeRedirect.startsWith('exp://') ? runtimeRedirect : PRODUCTION_REDIRECT_URI;
}

/**
 * O endereço da borda do ambiente local (DEV-0181): o configurado em `EXPO_PUBLIC_FITHUB_URL` ou, em desenvolvimento no
 * Expo Go, o IP do PC que o Metro usa, na porta da borda. Assim o endereço do PC fica num lugar só (FITHUB_HOST, no
 * compose). Fora disso, nenhum: valem os endereços de cada serviço.
 */
export function edgeUrlFor(dev: boolean, runtimeRedirect: string, configured?: string): string | undefined {
    if (configured && configured.length > 0) return configured.replace(/\/+$/, '');
    if (!dev) return undefined;
    const host = /^exp:\/\/([^:/]+)/.exec(runtimeRedirect)?.[1];
    return host ? `http://${host}:${LOCAL_EDGE_PORT}` : undefined;
}

const dev = typeof __DEV__ !== 'undefined' && __DEV__;
const runtimeRedirect = runtimeRedirectUri();
const edge = edgeUrlFor(dev, runtimeRedirect, process.env.EXPO_PUBLIC_FITHUB_URL);

// Um serviço configurado à parte vale sobre a borda; sem nenhum dos dois, o serviço rodando na própria máquina.
function serviceUrl(configured: string | undefined, edgePath: string, fallback: string): string {
    return setting(configured, edge ? `${edge}${edgePath}` : fallback);
}

const issuer = `${serviceUrl(process.env.EXPO_PUBLIC_KEYCLOAK_URL, '/auth', DEFAULT_KEYCLOAK_URL)}/realms/fithub`;

/** Client público do app do cliente, só com Authorization Code + PKCE (ADR-007). */
export const authConfig = {
    clientId: 'fithub-mobile',
    redirectUri: redirectUriFor(dev, runtimeRedirect),
    /** `offline_access`: o refresh token sobrevive ao fechamento do app, até o logout ou a revogação. */
    scopes: ['openid', 'offline_access'],
    endpoints: {
        authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
        tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
        endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
    },
} as const;

/** Endereço do client-service. */
export function clientApiUrl(): string {
    return serviceUrl(process.env.EXPO_PUBLIC_CLIENT_API_URL, '/api/client', DEFAULT_CLIENT_API_URL);
}

/** Endereço do training-service (DEV-0070). */
export function trainingApiUrl(): string {
    return serviceUrl(process.env.EXPO_PUBLIC_TRAINING_API_URL, '/api/training', DEFAULT_TRAINING_API_URL);
}

/** Endereço do nutrition-service (DEV-0089). */
export function nutritionApiUrl(): string {
    return serviceUrl(process.env.EXPO_PUBLIC_NUTRITION_API_URL, '/api/nutrition', DEFAULT_NUTRITION_API_URL);
}

/**
 * Endereços que podem receber o access token: só as APIs do FitHub, com o caminho (DEV-0178). Em produção, elas dividem a
 * origem com o Keycloak e os buckets das fotos (ADR-014), então a origem sozinha não basta.
 */
export function apiBases(): string[] {
    return [clientApiUrl(), trainingApiUrl(), nutritionApiUrl()];
}
