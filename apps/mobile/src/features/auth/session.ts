/**
 * Sessão do cliente no app (DEV-0028). Lógica pura, sem Expo: o login PKCE fica no `AuthGateway` e o
 * cofre do sistema no `TokenStore`. O access token vive só em memória; o refresh token só no cofre.
 */

/** Tokens devolvidos pelo Keycloak. `expiresAt` em milissegundos desde a época. */
export interface Tokens {
    accessToken: string;
    refreshToken: string;
    expiresAt: number;
}

/** O Keycloak recusou o refresh token (vencido, revogado, sessão encerrada): só resta entrar de novo. */
export class InvalidSessionError extends Error {
    constructor() {
        super('Sessão encerrada. Entre de novo.');
        this.name = 'InvalidSessionError';
    }
}

/** Fala com o Keycloak. Erro que não seja {@link InvalidSessionError} é falha passageira (rede, servidor). */
export interface AuthGateway {
    /** Abre o login no navegador do sistema. `undefined` quando a pessoa fecha sem entrar. */
    signIn(): Promise<Tokens | undefined>;
    /** Troca o refresh token por tokens novos. Lança {@link InvalidSessionError} se o Keycloak recusar. */
    refresh(refreshToken: string): Promise<Tokens>;
    /** Revoga a sessão no Keycloak. */
    signOut(refreshToken: string): Promise<void>;
}

/** Cofre do sistema para o refresh token (Keychain no iOS, Keystore no Android). */
export interface TokenStore {
    load(): Promise<string | undefined>;
    save(refreshToken: string): Promise<void>;
    clear(): Promise<void>;
}

/**
 * - `loading`: lendo o cofre e retomando a sessão.
 * - `signedIn` / `signedOut`: com ou sem sessão.
 * - `unavailable`: há sessão guardada, mas o Keycloak não respondeu; dá para tentar de novo.
 */
export type SessionStatus = 'loading' | 'signedIn' | 'signedOut' | 'unavailable';

/** Renova o access token quando faltar menos que isto para vencer. */
export const MIN_VALIDITY_MS = 60_000;

export interface Session {
    status(): SessionStatus;
    subscribe(listener: (status: SessionStatus) => void): () => void;
    /** Retoma a sessão guardada, se houver. Chamado ao abrir o app e em "tentar de novo". */
    restore(): Promise<void>;
    signIn(): Promise<void>;
    signOut(): Promise<void>;
    /**
     * Access token válido para o cabeçalho Authorization; `force` renova sempre (depois de um 401).
     * Lança {@link InvalidSessionError} sem sessão; falha passageira é relançada sem encerrar a sessão.
     */
    getAccessToken(options?: { force?: boolean }): Promise<string>;
    /** A API recusou até o token renovado: encerra a sessão local. */
    reject(): Promise<void>;
}

export function createSession(
    gateway: AuthGateway,
    store: TokenStore,
    now: () => number = Date.now,
): Session {
    let status: SessionStatus = 'loading';
    let tokens: Tokens | undefined;
    // Uma renovação por vez: o refresh token pode ser de uso único, e duas trocas ao mesmo tempo
    // invalidariam uma à outra.
    let renewal: Promise<Tokens> | undefined;
    const listeners = new Set<(status: SessionStatus) => void>();

    const setStatus = (next: SessionStatus) => {
        if (next === status) return;
        status = next;
        listeners.forEach((listener) => listener(next));
    };

    const forget = async () => {
        tokens = undefined;
        await store.clear();
        setStatus('signedOut');
    };

    const renew = (refreshToken: string): Promise<Tokens> => {
        renewal ??= gateway
            .refresh(refreshToken)
            .then(async (fresh) => {
                tokens = fresh;
                await store.save(fresh.refreshToken);
                return fresh;
            })
            .finally(() => {
                renewal = undefined;
            });
        return renewal;
    };

    return {
        status: () => status,
        subscribe(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        async restore() {
            setStatus('loading');
            let refreshToken: string | undefined;
            try {
                refreshToken = tokens?.refreshToken ?? (await store.load());
            } catch {
                // Cofre ilegível (aparelho restaurado, chave perdida): trata como sem sessão.
                refreshToken = undefined;
            }
            if (!refreshToken) {
                setStatus('signedOut');
                return;
            }
            try {
                await renew(refreshToken);
                setStatus('signedIn');
            } catch (error) {
                if (error instanceof InvalidSessionError) await forget();
                else setStatus('unavailable');
            }
        },
        async signIn() {
            const fresh = await gateway.signIn();
            if (!fresh) return;
            tokens = fresh;
            await store.save(fresh.refreshToken);
            setStatus('signedIn');
        },
        async signOut() {
            const refreshToken = tokens?.refreshToken;
            // Apaga primeiro: mesmo sem rede para revogar, nada da sessão fica no aparelho.
            await forget();
            if (refreshToken) await gateway.signOut(refreshToken).catch(() => undefined);
        },
        async getAccessToken(options) {
            if (!tokens) throw new InvalidSessionError();
            if (!options?.force && tokens.expiresAt - now() > MIN_VALIDITY_MS) return tokens.accessToken;
            try {
                return (await renew(tokens.refreshToken)).accessToken;
            } catch (error) {
                if (error instanceof InvalidSessionError) await forget();
                throw error;
            }
        },
        reject: forget,
    };
}
