import { act, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '@/App';

import { rolesFromClaims } from './authClient';
import { SessionExpiredError, useAuth, type AuthState } from './authContext';
import { AuthProvider } from './AuthProvider';
import { ProtectedRoute } from './ProtectedRoute';
import { fakeAuthClient, userWith, type FakeAuthClient } from './fakeAuthClient';
import { cameBackFromLogin, markLoginRedirect, REDIRECT_WINDOW_MS } from './loginRedirect';

// A área do profissional mostra o status do serviço, que não é assunto destes testes.
vi.mock('@/features/status/ServiceStatus', () => ({ ServiceStatus: () => null }));
// O onboarding tem os testes dele (OnboardingPage.test.tsx); aqui só importa ser levado até ele.
vi.mock('@/features/onboarding/OnboardingPage', () => ({ OnboardingPage: () => <h2>Onboarding</h2> }));
// As telas de clientes e convites têm os testes delas (clients.test.tsx).
vi.mock('@/features/clients/ClientsPage', () => ({ ClientsPage: () => null }));
vi.mock('@/features/clients/InvitationsPage', () => ({ InvitationsPage: () => null }));
vi.mock('@/features/clients/ClientDetailPage', () => ({ ClientDetailPage: () => null }));
vi.mock('@/features/assessments/AssessmentFormPage', () => ({ AssessmentFormPage: () => null }));
// O catálogo de exercícios tem os testes dele (exercises.test.tsx); aqui só importa quem chega até ele.
vi.mock('@/features/exercises/ExercisesPage', () => ({ ExercisesPage: () => <h3>Catálogo</h3> }));
vi.mock('@/features/exercises/ExerciseFormPage', () => ({ ExerciseFormPage: () => null }));
vi.mock('@/features/plans/ExpiringPlansPage', () => ({ ExpiringPlansPage: () => <h3>Vencendo</h3> }));

let auth: AuthState;
function AuthProbe() {
    const state = useAuth();
    useEffect(() => {
        auth = state;
    }, [state]);
    return null;
}

function renderApp(client: FakeAuthClient, path = '/app') {
    return render(
        <AuthProvider client={client}>
            <AuthProbe />
            <MemoryRouter initialEntries={[path]}>
                <App />
            </MemoryRouter>
        </AuthProvider>,
    );
}

const logins = (client: FakeAuthClient) => client.calls.filter((call) => call.startsWith('login:'));

beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('rota protegida', () => {
    it('sem destino para quem não tem o papel, nega o acesso e não mostra o conteúdo', async () => {
        const client = fakeAuthClient({ user: userWith('client') });
        render(
            <AuthProvider client={client}>
                <MemoryRouter>
                    <ProtectedRoute roles={['admin']}>
                        <p>Conteúdo restrito</p>
                    </ProtectedRoute>
                </MemoryRouter>
            </AuthProvider>,
        );

        expect(await screen.findByRole('alert')).toHaveTextContent('Acesso negado');
        expect(screen.queryByText('Conteúdo restrito')).not.toBeInTheDocument();
    });

    it('sem sessão, leva ao login uma vez e volta para a página pedida', async () => {
        const client = fakeAuthClient();
        renderApp(client);

        expect(screen.getByRole('status')).toHaveTextContent('Carregando…');
        await waitFor(() => expect(logins(client)).toEqual([`login:${window.location.href}`]));
        expect(screen.getByRole('status')).toHaveTextContent('Redirecionando para o login…');
        expect(screen.queryByText('Área do profissional')).not.toBeInTheDocument();
        expect(cameBackFromLogin()).toBe(true);
    });

    it.each(['personal', 'nutritionist'] as const)(
        'com o papel %s, mostra a área do profissional',
        async (role) => {
            const client = fakeAuthClient({ user: userWith('client', role) });
            renderApp(client);

            expect(await screen.findByText('Área do profissional')).toBeInTheDocument();
            expect(screen.getByText('Ana Teste')).toBeInTheDocument();
            expect(logins(client)).toEqual([]);
        },
    );

    it.each([['client'], ['admin'], []] as const)(
        'com os papéis [%s], leva ao onboarding e não mostra a área (DEV-0031)',
        async (...roles) => {
            const client = fakeAuthClient({ user: userWith(...roles) });
            renderApp(client);

            expect(await screen.findByRole('heading', { name: 'Onboarding' })).toBeInTheDocument();
            expect(screen.queryByText('Área do profissional')).not.toBeInTheDocument();
            expect(logins(client)).toEqual([]);
        },
    );

    it('quem voltou do login ainda sem sessão vê o erro, sem novo redirecionamento', async () => {
        markLoginRedirect();
        const client = fakeAuthClient();
        renderApp(client);

        expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível iniciar a sessão');
        expect(logins(client)).toEqual([]);

        await userEvent.click(screen.getByRole('button', { name: 'Entrar de novo' }));
        expect(logins(client)).toHaveLength(1);
    });

    it('falha do Keycloak ao iniciar mostra o erro, sem redirecionar', async () => {
        const client = fakeAuthClient({ initError: true });
        renderApp(client);

        expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível iniciar a sessão');
        expect(logins(client)).toEqual([]);
    });

    it('a página inicial é pública e não dispara login', async () => {
        const client = fakeAuthClient();
        renderApp(client, '/');

        expect(await screen.findByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/app');
        await waitFor(() => expect(client.calls).toContain('init'));
        expect(logins(client)).toEqual([]);
    });
});

describe('sessão', () => {
    it('logout encerra a sessão no Keycloak e volta para o início', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderApp(client);

        await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
        expect(client.calls).toContain(`logout:${window.location.origin}/`);
    });

    it('token vencido sem renovação encerra a sessão local e leva ao login', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderApp(client);
        await screen.findByText('Área do profissional');

        client.failRefresh();
        act(() => client.expireToken());

        await waitFor(() => expect(logins(client)).toHaveLength(1));
        expect(client.calls).toContain('clear');
        expect(screen.queryByText('Área do profissional')).not.toBeInTheDocument();
    });

    it('renova o token periodicamente e encerra a sessão quando a renovação falha', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const client = fakeAuthClient({ user: userWith('nutritionist') });
        renderApp(client);
        await screen.findByText('Área do profissional');

        await act(() => vi.advanceTimersByTimeAsync(30_000));
        expect(client.calls.filter((call) => call === 'refresh:60')).toHaveLength(1);
        expect(screen.getByText('Área do profissional')).toBeInTheDocument();

        client.failRefresh();
        await act(() => vi.advanceTimersByTimeAsync(30_000));
        await waitFor(() => expect(logins(client)).toHaveLength(1));
        // Uma falha, um envio ao login: sem laço.
        await act(() => vi.advanceTimersByTimeAsync(120_000));
        expect(logins(client)).toHaveLength(1);
    });

    it('getAccessToken devolve o token e, sem sessão, encerra e lança SessionExpiredError', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderApp(client);
        await screen.findByText('Área do profissional');

        await expect(auth.getAccessToken()).resolves.toBe('token-1');
        await expect(auth.getAccessToken({ force: true })).resolves.toBe('token-2');

        client.failRefresh();
        await act(async () => {
            await expect(auth.getAccessToken()).rejects.toBeInstanceOf(SessionExpiredError);
        });
        expect(client.calls).toContain('clear');
    });

    it('nada da sessão vai para o armazenamento do navegador', async () => {
        const client = fakeAuthClient({ user: userWith('personal') });
        renderApp(client);
        await screen.findByText('Área do profissional');
        await auth.getAccessToken();

        const stored = JSON.stringify([{ ...window.localStorage }, { ...window.sessionStorage }]);
        expect(stored).not.toContain('token-1');
        expect(window.localStorage).toHaveLength(0);
    });
});

describe('trava de redirecionamento', () => {
    it('vale só dentro da janela e só depois de um envio ao login', () => {
        expect(cameBackFromLogin()).toBe(false);
        markLoginRedirect(1_000);
        expect(cameBackFromLogin(1_000 + REDIRECT_WINDOW_MS - 1)).toBe(true);
        expect(cameBackFromLogin(1_000 + REDIRECT_WINDOW_MS)).toBe(false);
        // Relógio que andou para trás não conta como "há pouco".
        expect(cameBackFromLogin(999)).toBe(false);
    });
});

describe('papéis do token', () => {
    it('lê só os papéis conhecidos de realm_access.roles', () => {
        expect(
            rolesFromClaims({ realm_access: { roles: ['client', 'personal', 'offline_access', 7] } }),
        ).toEqual(['client', 'personal']);
    });

    it.each([
        undefined,
        null,
        'x',
        {},
        { realm_access: null },
        { realm_access: { roles: 'personal' } },
        { roles: ['admin'] },
    ])('claim ausente ou malformada (%j) não dá papel nenhum', (claims) => {
        expect(rolesFromClaims(claims)).toEqual([]);
    });
});

describe('catálogo de exercícios', () => {
    it('é do personal: o link aparece e a página abre', async () => {
        renderApp(fakeAuthClient({ user: userWith('personal') }), '/app/exercicios');

        expect(await screen.findByRole('heading', { name: 'Catálogo' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Exercícios' })).toHaveAttribute('href', '/app/exercicios');
    });

    it('a nutricionista não vê o link nem a página', async () => {
        renderApp(fakeAuthClient({ user: userWith('nutritionist') }), '/app/exercicios');

        expect(await screen.findByRole('alert')).toHaveTextContent('Esta área é só para o personal.');
        expect(screen.queryByRole('heading', { name: 'Catálogo' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Exercícios' })).not.toBeInTheDocument();
    });
});

describe('fichas vencendo', () => {
    it('é do personal: o link aparece e a página abre', async () => {
        renderApp(fakeAuthClient({ user: userWith('personal') }), '/app/fichas-vencendo');

        expect(await screen.findByRole('heading', { name: 'Vencendo' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Fichas vencendo' })).toHaveAttribute(
            'href',
            '/app/fichas-vencendo',
        );
    });

    it('a nutricionista não vê o link nem a página', async () => {
        renderApp(fakeAuthClient({ user: userWith('nutritionist') }), '/app/fichas-vencendo');

        expect(await screen.findByRole('alert')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Vencendo' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Fichas vencendo' })).not.toBeInTheDocument();
    });
});
