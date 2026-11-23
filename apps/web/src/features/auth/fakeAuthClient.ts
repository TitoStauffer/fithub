import { RefreshRejectedError, type AuthClient, type AuthUser, type Role } from './authClient';

/** Sessão falsa para os testes: registra as chamadas e deixa controlar o resultado de cada passo. */
export interface FakeAuthClient extends AuthClient {
    calls: string[];
    /** Dispara o aviso de token vencido, como o keycloak-js faria. */
    expireToken(): void;
    /**
     * A partir daqui a renovação falha: `rejected` (padrão) é o Keycloak recusando, `offline` é rede fora.
     */
    failRefresh(kind?: 'rejected' | 'offline'): void;
    /** O token de acesso atual passa a estar vencido. */
    expireAccessToken(): void;
    /** A próxima renovação traz estes papéis (o Keycloak atribuiu um papel novo). */
    grantRoles(...roles: Role[]): void;
}

export function userWith(...roles: Role[]): AuthUser {
    return { subject: 'user-1', name: 'Ana Teste', email: 'ana@fithub.local', roles };
}

export function fakeAuthClient(options: { user?: AuthUser; initError?: boolean } = {}): FakeAuthClient {
    let user = options.user;
    let token = user ? 'token-1' : undefined;
    let refreshFailure: 'rejected' | 'offline' | undefined;
    let tokenValid = true;
    let pendingRoles: Role[] | undefined;
    let renewals = 0;
    let expiredListener: (() => void) | undefined;
    const calls: string[] = [];

    return {
        calls,
        init() {
            calls.push('init');
            return options.initError
                ? Promise.reject(new Error('Keycloak fora do ar'))
                : Promise.resolve(Boolean(user));
        },
        login(returnTo) {
            calls.push(`login:${returnTo}`);
            return Promise.resolve();
        },
        logout(returnTo) {
            calls.push(`logout:${returnTo}`);
            user = undefined;
            token = undefined;
            return Promise.resolve();
        },
        refresh(minValiditySeconds) {
            calls.push(`refresh:${minValiditySeconds}`);
            if (refreshFailure === 'offline') return Promise.reject(new TypeError('Failed to fetch'));
            if (refreshFailure === 'rejected' || !user) return Promise.reject(new RefreshRejectedError());
            if (minValiditySeconds < 0) {
                renewals += 1;
                token = `token-${renewals + 1}`;
                tokenValid = true;
                if (pendingRoles && user) {
                    user = { ...user, roles: pendingRoles };
                    pendingRoles = undefined;
                }
            }
            return Promise.resolve();
        },
        accessToken: () => token,
        hasValidToken: () => Boolean(token) && tokenValid,
        user: () => user,
        clear() {
            calls.push('clear');
            user = undefined;
            token = undefined;
        },
        onTokenExpired(listener) {
            expiredListener = listener;
        },
        expireToken: () => expiredListener?.(),
        failRefresh(kind = 'rejected') {
            refreshFailure = kind;
        },
        expireAccessToken() {
            tokenValid = false;
        },
        grantRoles(...roles) {
            pendingRoles = roles;
        },
    };
}
