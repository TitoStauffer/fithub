import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

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

    const endSession = useCallback(() => {
        client.clear();
        setSession({ status: 'anonymous', user: undefined });
    }, [client]);

    const rejectSession = useCallback(() => {
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
                try {
                    await client.refresh(options?.force ? -1 : MIN_VALIDITY_SECONDS);
                } catch (error) {
                    if (error instanceof RefreshRejectedError) {
                        endSession();
                        throw new SessionExpiredError();
                    }
                    // Falha passageira: o token atual ainda serve, se estiver na validade.
                    if (!client.hasValidToken()) throw new SessionUnavailableError();
                }
                const token = client.accessToken();
                if (!token) {
                    endSession();
                    throw new SessionExpiredError();
                }
                return token;
            },
            endSession,
            rejectSession,
        }),
        [client, endSession, rejectSession, session],
    );

    return <AuthContext value={state}>{children}</AuthContext>;
}
