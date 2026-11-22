import { InvalidSessionError, type AuthGateway, type TokenStore, type Tokens } from './session';

/** Relógio fixo dos testes. */
export const NOW = 1_000_000;

export function tokens(suffix: string, expiresInMs = 300_000): Tokens {
    return {
        accessToken: `access-${suffix}`,
        refreshToken: `refresh-${suffix}`,
        expiresAt: NOW + expiresInMs,
    };
}

export interface FakeStore extends TokenStore {
    value: string | undefined;
    saved: string[];
    clears: number;
    failLoad: boolean;
}

export function fakeStore(initial?: string): FakeStore {
    const store: FakeStore = {
        value: initial,
        saved: [],
        clears: 0,
        failLoad: false,
        load() {
            return store.failLoad
                ? Promise.reject(new Error('cofre ilegível'))
                : Promise.resolve(store.value);
        },
        save(refreshToken) {
            store.value = refreshToken;
            store.saved.push(refreshToken);
            return Promise.resolve();
        },
        clear() {
            store.value = undefined;
            store.clears += 1;
            return Promise.resolve();
        },
    };
    return store;
}

export type RefreshMode = 'ok' | 'invalid' | 'offline';

export interface FakeGateway extends AuthGateway {
    calls: string[];
    refreshMode: RefreshMode;
    signInResult: Tokens | undefined | Error;
    signOutFails: boolean;
}

export function fakeGateway(): FakeGateway {
    let renewals = 0;
    const gateway: FakeGateway = {
        calls: [],
        refreshMode: 'ok',
        signInResult: tokens('login'),
        signOutFails: false,
        signIn() {
            gateway.calls.push('signIn');
            return gateway.signInResult instanceof Error
                ? Promise.reject(gateway.signInResult)
                : Promise.resolve(gateway.signInResult);
        },
        refresh(refreshToken) {
            gateway.calls.push(`refresh:${refreshToken}`);
            if (gateway.refreshMode === 'invalid') return Promise.reject(new InvalidSessionError());
            if (gateway.refreshMode === 'offline') return Promise.reject(new Error('sem rede'));
            renewals += 1;
            return Promise.resolve(tokens(`renewed-${renewals}`));
        },
        signOut(refreshToken) {
            gateway.calls.push(`signOut:${refreshToken}`);
            return gateway.signOutFails ? Promise.reject(new Error('sem rede')) : Promise.resolve();
        },
    };
    return gateway;
}
