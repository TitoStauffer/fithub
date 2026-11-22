/** Configuração do app lida das variáveis VITE_* (vite-env.d.ts), com os padrões do ambiente local. */
function setting(value: unknown, fallback: string): string {
    return typeof value === 'string' && value.length > 0 ? value : fallback;
}

export const config = {
    /** Keycloak do compose local (infra/README.md). */
    keycloakUrl: setting(import.meta.env.VITE_KEYCLOAK_URL, 'http://localhost:8080'),
    keycloakRealm: 'fithub',
    /** Client público do app do profissional, só com Authorization Code + PKCE (ADR-007). */
    keycloakClientId: 'fithub-web',
    accountApiUrl: setting(import.meta.env.VITE_ACCOUNT_API_URL, 'http://localhost:8081'),
} as const;

/** Origens que podem receber o token de acesso: só as APIs do FitHub. */
export function apiOrigins(): string[] {
    return [new URL(config.accountApiUrl).origin];
}
