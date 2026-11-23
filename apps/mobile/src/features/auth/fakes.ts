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
    failSave: boolean;
    /** Quantas chamadas de `clear` ainda falham (cofre com defeito). */
    failClears: number;
    /** A próxima gravação fica pendente até a função devolvida ser chamada (cofre lento). */
    holdNextSave(): () => void;
}

export function fakeStore(initial?: string): FakeStore {
    let saveGate: Promise<void> | undefined;
    const store: FakeStore = {
        holdNextSave() {
            let release!: () => void;
            saveGate = new Promise<void>((resolve) => {
                release = resolve;
            });
            return release;
        },
        value: initial,
        saved: [],
        clears: 0,
        failLoad: false,
        failSave: false,
        failClears: 0,
        load() {
            return store.failLoad
                ? Promise.reject(new Error('cofre ilegível'))
                : Promise.resolve(store.value);
        },
        async save(refreshToken) {
            const pending = saveGate;
            saveGate = undefined;
            await pending;
            if (store.failSave) throw new Error('cofre cheio');
            store.value = refreshToken;
            store.saved.push(refreshToken);
        },
        clear() {
            store.clears += 1;
            if (store.failClears > 0) {
                store.failClears -= 1;
                return Promise.reject(new Error('cofre travado'));
            }
            store.value = undefined;
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
    /** A próxima renovação fica pendente até a função devolvida ser chamada (Keycloak lento). */
    holdNextRefresh(): () => void;
}

export function fakeGateway(): FakeGateway {
    let renewals = 0;
    let gate: Promise<void> | undefined;
    const gateway: FakeGateway = {
        calls: [],
        refreshMode: 'ok',
        signInResult: tokens('login'),
        signOutFails: false,
        holdNextRefresh() {
            let release!: () => void;
            gate = new Promise<void>((resolve) => {
                release = resolve;
            });
            return release;
        },
        signIn() {
            gateway.calls.push('signIn');
            return gateway.signInResult instanceof Error
                ? Promise.reject(gateway.signInResult)
                : Promise.resolve(gateway.signInResult);
        },
        async refresh(refreshToken) {
            gateway.calls.push(`refresh:${refreshToken}`);
            const pending = gate;
            gate = undefined;
            await pending;
            if (gateway.refreshMode === 'invalid') throw new InvalidSessionError();
            if (gateway.refreshMode === 'offline') throw new Error('sem rede');
            renewals += 1;
            return tokens(`renewed-${renewals}`);
        },
        signOut(refreshToken) {
            gateway.calls.push(`signOut:${refreshToken}`);
            return gateway.signOutFails ? Promise.reject(new Error('sem rede')) : Promise.resolve();
        },
    };
    return gateway;
}
