import type { AuthClient, AuthUser, Role } from './authClient';

/** Sessão falsa para os testes: registra as chamadas e deixa controlar o resultado de cada passo. */
export interface FakeAuthClient extends AuthClient {
    calls: string[];
    /** Dispara o aviso de token vencido, como o keycloak-js faria. */
    expireToken(): void;
    /** A partir daqui a renovação falha (sessão encerrada no Keycloak). */
    failRefresh(): void;
}

export function userWith(...roles: Role[]): AuthUser {
    return { subject: 'user-1', name: 'Ana Teste', email: 'ana@fithub.local', roles };
}

export function fakeAuthClient(options: { user?: AuthUser; initError?: boolean } = {}): FakeAuthClient {
    let user = options.user;
    let token = user ? 'token-1' : undefined;
    let refreshFails = false;
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
            if (refreshFails || !user) return Promise.reject(new Error('sessão encerrada'));
            if (minValiditySeconds < 0) {
                renewals += 1;
                token = `token-${renewals + 1}`;
            }
            return Promise.resolve();
        },
        accessToken: () => token,
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
        failRefresh() {
            refreshFails = true;
        },
    };
}
