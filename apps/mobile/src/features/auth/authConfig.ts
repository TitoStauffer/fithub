const DEFAULT_KEYCLOAK_URL = 'http://localhost:8080';
const DEFAULT_CLIENT_API_URL = 'http://localhost:8082';
const DEFAULT_TRAINING_API_URL = 'http://localhost:8083';

function setting(value: string | undefined, fallback: string): string {
    return value && value.length > 0 ? value : fallback;
}

// No emulador Android, localhost é o próprio aparelho: aponte as variáveis para o IP da máquina.
const issuer = `${setting(process.env.EXPO_PUBLIC_KEYCLOAK_URL, DEFAULT_KEYCLOAK_URL)}/realms/fithub`;

/** Client público do app do cliente, só com Authorization Code + PKCE (ADR-007). */
export const authConfig = {
    clientId: 'fithub-mobile',
    /** A única redirect URI aceita pelo realm para este client. */
    redirectUri: 'fithub://auth',
    /** `offline_access`: o refresh token sobrevive ao fechamento do app, até o logout ou a revogação. */
    scopes: ['openid', 'offline_access'],
    endpoints: {
        authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
        tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
        endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
    },
} as const;

/** Endereço do client-service; no emulador Android, aponte para o IP da máquina. */
export function clientApiUrl(): string {
    return setting(process.env.EXPO_PUBLIC_CLIENT_API_URL, DEFAULT_CLIENT_API_URL);
}

/** Endereço do training-service (DEV-0070); no emulador Android, aponte para o IP da máquina. */
export function trainingApiUrl(): string {
    return setting(process.env.EXPO_PUBLIC_TRAINING_API_URL, DEFAULT_TRAINING_API_URL);
}

/** Origens que podem receber o access token: só as APIs do FitHub. */
export function apiOrigins(): string[] {
    return [originOf(clientApiUrl()), originOf(trainingApiUrl())];
}

/**
 * Origem (`esquema://host[:porta]`) de uma URL absoluta http(s). Devolve `''` para qualquer outra coisa
 * (URL relativa, inválida, outro esquema), que nunca casa com uma origem permitida. Usa o `URL` padrão
 * que o Expo fornece, em vez de interpretar a URL à mão.
 */
export function originOf(url: string): string {
    try {
        const parsed = new URL(url);
        return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.origin : '';
    } catch {
        return '';
    }
}
