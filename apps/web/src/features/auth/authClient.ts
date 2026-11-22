import Keycloak from 'keycloak-js';

import { config } from '@/lib/config';

/** Papéis de realm do FitHub (ADR-007). */
export type Role = 'client' | 'personal' | 'nutritionist' | 'admin';
const ROLES: readonly string[] = ['client', 'personal', 'nutritionist', 'admin'];

/** O que o app usa do token: nada além disso sai do adaptador. */
export interface AuthUser {
    subject: string;
    name: string;
    email: string;
    roles: Role[];
}

/**
 * Sessão com o provedor de identidade. A implementação real é o keycloak-js; os testes usam uma
 * falsa. Os tokens ficam só na memória da implementação: o app pede o token na hora de chamar a API.
 */
export interface AuthClient {
    /** Processa a volta do login ou tenta retomar a sessão do Keycloak. Devolve se há sessão. */
    init(): Promise<boolean>;
    /** Leva ao login do Keycloak e volta para `returnTo`. */
    login(returnTo: string): Promise<void>;
    /** Encerra a sessão no Keycloak e volta para `returnTo`. */
    logout(returnTo: string): Promise<void>;
    /** Renova o token se ele vence em menos de `minValiditySeconds` (-1 força). Lança se não conseguir. */
    refresh(minValiditySeconds: number): Promise<void>;
    /** Token de acesso atual, só para o cabeçalho Authorization das APIs do FitHub. */
    accessToken(): string | undefined;
    user(): AuthUser | undefined;
    /** Apaga os tokens da memória, sem falar com o Keycloak. */
    clear(): void;
    /** Avisa quando o token venceu sem ter sido renovado. */
    onTokenExpired(listener: () => void): void;
}

/** Papéis conhecidos de `realm_access.roles`; claim ausente ou malformada resulta em nenhum papel. */
export function rolesFromClaims(claims: unknown): Role[] {
    if (typeof claims !== 'object' || claims === null) return [];
    const realmAccess: unknown = (claims as Record<string, unknown>).realm_access;
    if (typeof realmAccess !== 'object' || realmAccess === null) return [];
    const roles: unknown = (realmAccess as Record<string, unknown>).roles;
    if (!Array.isArray(roles)) return [];
    return roles.filter((role): role is Role => typeof role === 'string' && ROLES.includes(role));
}

const text = (value: unknown): string => (typeof value === 'string' ? value : '');

/** Cliente real: keycloak-js em Authorization Code + PKCE S256, com os tokens só em memória. */
export function createKeycloakClient(): AuthClient {
    const keycloak = new Keycloak({
        url: config.keycloakUrl,
        realm: config.keycloakRealm,
        clientId: config.keycloakClientId,
    });
    // O keycloak-js só aceita um init por instância; o StrictMode do React monta os efeitos duas vezes.
    let initialization: Promise<boolean> | undefined;

    return {
        init() {
            initialization ??= keycloak.init({
                flow: 'standard',
                pkceMethod: 'S256',
                // Retoma a sessão por um iframe escondido, sem tirar a pessoa da página. Se o navegador
                // bloquear, o app fica sem sessão e a rota protegida leva ao login.
                onLoad: 'check-sso',
                silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
                silentCheckSsoFallback: false,
                // Sem o iframe de vigia de sessão: a renovação do token já detecta sessão encerrada.
                checkLoginIframe: false,
            });
            return initialization;
        },
        login: (returnTo) => keycloak.login({ redirectUri: returnTo }),
        logout: (returnTo) => keycloak.logout({ redirectUri: returnTo }),
        async refresh(minValiditySeconds) {
            await keycloak.updateToken(minValiditySeconds);
        },
        accessToken: () => keycloak.token,
        user() {
            const claims = keycloak.tokenParsed;
            if (!keycloak.authenticated || !claims?.sub) return undefined;
            return {
                subject: claims.sub,
                name: text(claims.name),
                email: text(claims.email),
                roles: rolesFromClaims(claims),
            };
        },
        clear() {
            void keycloak.clearToken();
        },
        onTokenExpired(listener) {
            keycloak.onTokenExpired = listener;
        },
    };
}
