/** Configuração do app lida das variáveis VITE_* (vite-env.d.ts), com os padrões do ambiente local. */

/**
 * Endereço absoluto a partir do configurado. Um caminho (`/api/account`, `/auth`) vale na origem da página: em
 * produção a web, as APIs e o Keycloak ficam no mesmo domínio atrás do Traefik, e a mesma imagem serve qualquer
 * domínio (DEV-0176, ADR-014). Um endereço completo fica como está.
 */
export function resolveUrl(value: string, origin: string = window.location.origin): string {
    if (!value.startsWith('/')) return value;
    return new URL(value, origin).href.replace(/\/$/, '');
}

function setting(value: unknown, fallback: string): string {
    return resolveUrl(typeof value === 'string' && value.length > 0 ? value : fallback);
}

export const config = {
    /** Keycloak do compose local (infra/README.md); em produção, `/auth`. */
    keycloakUrl: setting(import.meta.env.VITE_KEYCLOAK_URL, 'http://localhost:8080'),
    keycloakRealm: 'fithub',
    /** Client público do app do profissional, só com Authorization Code + PKCE (ADR-007). */
    keycloakClientId: 'fithub-web',
    accountApiUrl: setting(import.meta.env.VITE_ACCOUNT_API_URL, 'http://localhost:8081'),
    clientApiUrl: setting(import.meta.env.VITE_CLIENT_API_URL, 'http://localhost:8082'),
    trainingApiUrl: setting(import.meta.env.VITE_TRAINING_API_URL, 'http://localhost:8083'),
    nutritionApiUrl: setting(import.meta.env.VITE_NUTRITION_API_URL, 'http://localhost:8084'),
} as const;

/** Endereços que podem receber o token de acesso: só os das APIs do FitHub, com o caminho (DEV-0176). */
export function apiBases(): string[] {
    return [config.accountApiUrl, config.clientApiUrl, config.trainingApiUrl, config.nutritionApiUrl];
}
