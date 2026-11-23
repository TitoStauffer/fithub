import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { RefreshRejectedError, type AuthClient, type AuthUser } from './authClient';
import {
    AuthContext,
    SessionExpiredError,
    SessionUnavailableError,
    type AuthState,
    type AuthStatus,
} from './authContext';
import { cameBackFromLogin, clearLoginRedirect, markLoginRedirect } from './loginRedirect';

/** Renova o token quando faltar menos que isto para vencer (o access token do realm dura 5 minutos). */
const MIN_VALIDITY_SECONDS = 60;
const REFRESH_CHECK_MS = 30_000;

interface Session {
    status: AuthStatus;
    user: AuthUser | undefined;
}

/**
 * Sessão do profissional (DEV-0027): descobre a sessão ao montar, mantém o token renovado e encerra a
 * sessão local quando a renovação falha. Os tokens ficam só no `client`, em memória.
 */
export function AuthProvider({ client, children }: { client: AuthClient; children: ReactNode }) {
    const [session, setSession] = useState<Session>({ status: 'loading', user: undefined });

    // A recusa da API é final até a pessoa sair (Code Review do PR #200): uma segunda chamada que
    // termina depois não pode trocar `rejected` por `anonymous`, senão a rota protegida manda ao login
    // e o laço volta. Fica numa ref porque as chamadas concorrentes não esperam o próximo render.
    const rejected = useRef(false);

    const endSession = useCallback(() => {
        client.clear();
        if (!rejected.current) setSession({ status: 'anonymous', user: undefined });
    }, [client]);

    const rejectSession = useCallback(() => {
        rejected.current = true;
        client.clear();
        setSession({ status: 'rejected', user: undefined });
    }, [client]);

    // Só a recusa do Keycloak encerra a sessão; rede fora por instantes não tira a pessoa da tela (DEV-0146).
    const onRefreshFailure = useCallback(
        (error: unknown) => {
            if (error instanceof RefreshRejectedError) endSession();
        },
        [endSession],
    );

    useEffect(() => {
        let active = true;
        client.onTokenExpired(() => {
            client.refresh(MIN_VALIDITY_SECONDS).catch((error: unknown) => {
                if (active) onRefreshFailure(error);
            });
        });
        client
            .init()
            .then((authenticated) => {
                if (!active) return;
                const user = authenticated ? client.user() : undefined;
                if (user) {
                    clearLoginRedirect();
                    setSession({ status: 'authenticated', user });
                } else {
                    // Voltou do login ainda sem sessão: é falha, não motivo para redirecionar de novo.
                    setSession({ status: cameBackFromLogin() ? 'failed' : 'anonymous', user: undefined });
                }
            })
            .catch(() => {
                if (active) setSession({ status: 'failed', user: undefined });
            });
        return () => {
            active = false;
        };
    }, [client, onRefreshFailure]);

    // Com sessão, confere de tempos em tempos e renova antes de o token vencer.
    useEffect(() => {
        if (session.status !== 'authenticated') return;
        const timer = window.setInterval(() => {
            client
                .refresh(MIN_VALIDITY_SECONDS)
                .then(() => {
                    const user = client.user();
                    if (user) setSession({ status: 'authenticated', user });
                })
                .catch(onRefreshFailure);
        }, REFRESH_CHECK_MS);
        return () => window.clearInterval(timer);
    }, [client, onRefreshFailure, session.status]);

    const state = useMemo<AuthState>(
        () => ({
            status: session.status,
            user: session.user,
            login() {
                markLoginRedirect();
                return client.login(window.location.href);
            },
            logout() {
                clearLoginRedirect();
                return client.logout(`${window.location.origin}/`);
            },
            async getAccessToken(options) {
                if (rejected.current) throw new SessionExpiredError();
                try {
                    await client.refresh(options?.force ? -1 : MIN_VALIDITY_SECONDS);
                } catch (error) {
                    if (error instanceof RefreshRejectedError) {
                        endSession();
                        throw new SessionExpiredError();
                    }
                    // Falha passageira. Com `force` (depois de um 401) o token atual é justamente o que a
                    // API recusou: devolvê-lo levaria a tela de sessão recusada por uma queda de rede.
                    if (options?.force || !client.hasValidToken()) throw new SessionUnavailableError();
                }
                const token = client.accessToken();
                if (!token) {
                    endSession();
                    throw new SessionExpiredError();
                }
                return token;
            },
            async reloadSession() {
                if (rejected.current) throw new SessionExpiredError();
                try {
                    await client.refresh(-1);
                } catch (error) {
                    if (error instanceof RefreshRejectedError) {
                        endSession();
                        throw new SessionExpiredError();
                    }
                    throw new SessionUnavailableError();
                }
                const user = client.user();
                if (user) setSession({ status: 'authenticated', user });
            },
            endSession,
            rejectSession,
        }),
        [client, endSession, rejectSession, session],
    );

    return <AuthContext value={state}>{children}</AuthContext>;
}
