import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect, useState } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createApiFetch } from '@/lib/http';

import { SessionUnavailableError, useAuth, type AuthState } from './authContext';
import { AuthProvider } from './AuthProvider';
import { fakeAuthClient, userWith, type FakeAuthClient } from './fakeAuthClient';
import { ProtectedRoute } from './ProtectedRoute';

// DEV-0146 (achados do Code Review no PR #194): a sessão da web não cai por rede fora por instantes e
// não entra em laço de login quando a API recusa até o token renovado.

const API = 'http://localhost:8081';
let auth: AuthState;

function AuthProbe() {
    const state = useAuth();
    useEffect(() => {
        auth = state;
    }, [state]);
    return null;
}

/** Tela que chama a API ao montar, como o onboarding vai fazer (DEV-0031). */
function CallsApiOnMount({ fetch }: { fetch: typeof window.fetch }) {
    const { getAccessToken, rejectSession } = useAuth();
    const [result, setResult] = useState('carregando');
    useEffect(() => {
        const apiFetch = createApiFetch({
            getAccessToken,
            onSessionRejected: rejectSession,
            allowedOrigins: [API],
            fetch,
        });
        apiFetch(`${API}/professionals/me`).then(
            (response) => setResult(`status ${response.status}`),
            (error: Error) => setResult(error.name),
        );
    }, [fetch, getAccessToken, rejectSession]);
    return <p>{result}</p>;
}

function renderArea(client: FakeAuthClient, fetch: typeof window.fetch = vi.fn()) {
    return render(
        <AuthProvider client={client}>
            <AuthProbe />
            <MemoryRouter initialEntries={['/app']}>
                <Routes>
                    <Route
                        path="/app"
                        element={
                            <ProtectedRoute roles={['personal']}>
                                <h2>Área do profissional</h2>
                                <CallsApiOnMount fetch={fetch} />
                            </ProtectedRoute>
                        }
                    />
                </Routes>
            </MemoryRouter>
        </AuthProvider>,
    );
}

const logins = (client: FakeAuthClient) => client.calls.filter((call) => call.startsWith('login:'));
const respondWith = (...statuses: number[]) => {
    let call = 0;
    return vi.fn(() =>
        Promise.resolve(new Response(null, { status: statuses[Math.min(call++, statuses.length - 1)] })),
    );
};

beforeEach(() => {
    window.sessionStorage.clear();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('falha passageira de rede na renovação', () => {
    it('a renovação periódica sem rede não encerra a sessão nem leva ao login', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(200));
        await screen.findByText('Área do profissional');

        client.failRefresh('offline');
        await act(() => vi.advanceTimersByTimeAsync(90_000));

        expect(screen.getByText('Área do profissional')).toBeInTheDocument();
        expect(client.calls).not.toContain('clear');
        expect(logins(client)).toEqual([]);
    });

    it('token vencido sem renovação por falta de rede não encerra a sessão', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(200));
        await screen.findByText('Área do profissional');

        client.failRefresh('offline');
        act(() => client.expireToken());

        await waitFor(() => expect(client.calls).toContain('refresh:60'));
        expect(screen.getByText('Área do profissional')).toBeInTheDocument();
        expect(client.calls).not.toContain('clear');
    });

    it('sem rede e com o token ainda válido, a chamada usa o token atual', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(200));
        await screen.findByText('status 200');

        client.failRefresh('offline');
        await expect(auth.getAccessToken()).resolves.toBe('token-1');
        expect(client.calls).not.toContain('clear');
    });

    it('sem rede e com o token vencido, avisa que está sem conexão e mantém a sessão', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(200));
        await screen.findByText('status 200');

        client.failRefresh('offline');
        client.expireAccessToken();

        await expect(auth.getAccessToken()).rejects.toBeInstanceOf(SessionUnavailableError);
        expect(auth.status).toBe('authenticated');
        expect(client.calls).not.toContain('clear');
    });

    it('a recusa do Keycloak continua encerrando a sessão', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(200));
        await screen.findByText('status 200');

        client.failRefresh('rejected');
        act(() => client.expireToken());

        await waitFor(() => expect(client.calls).toContain('clear'));
        await waitFor(() => expect(logins(client)).toHaveLength(1));
    });
});

describe('API que recusa até o token renovado', () => {
    it('mostra o erro com a saída, sem redirecionar ao login nem repetir a chamada', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        const fetch = respondWith(401);
        renderArea(client, fetch);

        expect(await screen.findByRole('alert')).toHaveTextContent('Sua sessão não foi aceita');
        // Uma chamada e uma nova tentativa com o token renovado; depois, nada.
        await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
        await new Promise((resolve) => setTimeout(resolve, 50));
        expect(fetch).toHaveBeenCalledTimes(2);
        expect(logins(client)).toEqual([]);
        expect(client.calls).toContain('clear');
        expect(screen.queryByText('Área do profissional')).not.toBeInTheDocument();
    });

    it('"Sair e entrar de novo" encerra a sessão no Keycloak', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(401));

        await userEvent.click(await screen.findByRole('button', { name: 'Sair e entrar de novo' }));

        expect(client.calls).toContain(`logout:${window.location.origin}/`);
        expect(logins(client)).toEqual([]);
    });

    it('401 seguido de sucesso com o token renovado segue normal', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderArea(client, respondWith(401, 200));

        expect(await screen.findByText('status 200')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
});
