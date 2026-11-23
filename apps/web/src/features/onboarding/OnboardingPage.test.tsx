import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith, type FakeAuthClient } from '@/features/auth/fakeAuthClient';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';

import { OnboardingPage } from './OnboardingPage';

const API = 'http://localhost:8081/professionals/me';
const PROFESSIONAL = {
    id: '01937c4e-2222-7ccc-8ddd-000000000002',
    name: 'Ana Teste',
    type: 'PERSONAL',
    registration: {
        council: 'CREF',
        number: '012345',
        category: 'G',
        state: 'SP',
        display: 'CREF 012345-G/SP',
    },
    situation: 'DECLARED',
    roleStatus: 'ASSIGNED',
};

type Reply = { status: number; body?: unknown };
let replies: { GET: Reply[]; POST: Reply[] };
let posts: unknown[];

function json(reply: Reply) {
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
        status: reply.status,
        headers: { 'Content-Type': 'application/problem+json' },
    });
}

beforeEach(() => {
    replies = { GET: [{ status: 404 }], POST: [{ status: 201, body: PROFESSIONAL }] };
    posts = [];
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            if (url !== API) return Promise.reject(new Error(`URL inesperada: ${url}`));
            const method = (init?.method ?? 'GET') as 'GET' | 'POST';
            if (method === 'POST') posts.push(JSON.parse(typeof init?.body === 'string' ? init.body : '{}'));
            const queue = replies[method];
            return Promise.resolve(json(queue.length > 1 ? queue.shift()! : queue[0]!));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
});

function renderOnboarding(client: FakeAuthClient) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={client}>
                <MemoryRouter initialEntries={['/onboarding']}>
                    <Routes>
                        <Route
                            path="/onboarding"
                            element={
                                <ProtectedRoute>
                                    <OnboardingPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route path="/app" element={<h2>Área do profissional</h2>} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

async function fillPersonal(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByLabelText('Personal trainer (CREF)'));
    await user.type(screen.getByLabelText('Número do registro'), '012345');
    await user.selectOptions(screen.getByLabelText('Categoria do CREF'), 'G');
    await user.selectOptions(screen.getByLabelText('UF do registro'), 'SP');
}

describe('onboarding do profissional', () => {
    it('sem cadastro, mostra o formulário', async () => {
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));

        expect(
            await screen.findByRole('heading', { name: 'Complete seu cadastro de profissional' }),
        ).toBeInTheDocument();
    });

    it('quem já é profissional vai direto para a área', async () => {
        renderOnboarding(fakeAuthClient({ user: userWith('client', 'nutritionist') }));

        expect(await screen.findByRole('heading', { name: 'Área do profissional' })).toBeInTheDocument();
    });

    it('valida no navegador antes de enviar, com mensagens em cada campo', async () => {
        const user = userEvent.setup();
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));
        await screen.findByRole('heading', { name: 'Complete seu cadastro de profissional' });

        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

        expect(screen.getByText('Informe se você é personal ou nutricionista.')).toBeInTheDocument();
        expect(screen.getByText('Número do registro: de 1 a 6 dígitos.')).toBeInTheDocument();
        expect(screen.getByText('UF do registro inválida.')).toBeInTheDocument();
        expect(screen.getByLabelText('Número do registro')).toHaveAttribute('aria-invalid', 'true');
        expect(posts).toEqual([]);
    });

    it('personal precisa da categoria do CREF; nutricionista não tem categoria', async () => {
        const user = userEvent.setup();
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));

        await user.click(await screen.findByLabelText('Personal trainer (CREF)'));
        await user.type(screen.getByLabelText('Número do registro'), '123');
        await user.selectOptions(screen.getByLabelText('UF do registro'), 'RJ');
        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));
        expect(screen.getByText('Categoria do CREF: G ou P.')).toBeInTheDocument();
        expect(posts).toEqual([]);

        await user.click(screen.getByLabelText('Nutricionista (CRN)'));
        expect(screen.queryByLabelText('Categoria do CREF')).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));
        await waitFor(() => expect(posts).toHaveLength(1));
        expect(posts[0]).toEqual({
            type: 'NUTRITIONIST',
            registrationNumber: '123',
            registrationCategory: null,
            registrationState: 'RJ',
        });
    });

    it('depois do cadastro, renova a sessão e entra na área com o papel novo', async () => {
        const user = userEvent.setup();
        const client = fakeAuthClient({ user: userWith('client') });
        client.grantRoles('client', 'personal');
        renderOnboarding(client);

        await fillPersonal(user);
        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

        expect(await screen.findByRole('heading', { name: 'Área do profissional' })).toBeInTheDocument();
        expect(posts).toEqual([
            {
                type: 'PERSONAL',
                registrationNumber: '012345',
                registrationCategory: 'G',
                registrationState: 'SP',
            },
        ]);
        expect(client.calls).toContain('refresh:-1');
    });

    it('erro de campo da API aparece no campo, sem perder o que foi digitado', async () => {
        const user = userEvent.setup();
        replies.POST = [
            {
                status: 400,
                body: {
                    detail: 'Um ou mais campos são inválidos.',
                    errors: [{ field: 'registrationNumber', message: 'Registro recusado.' }],
                },
            },
        ];
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));

        await fillPersonal(user);
        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

        expect(await screen.findByText('Registro recusado.')).toBeInTheDocument();
        expect(screen.getByLabelText('Número do registro')).toHaveValue('012345');
        expect(screen.getByLabelText('UF do registro')).toHaveValue('SP');
    });

    it('conta com outro cadastro mostra a mensagem da API no formulário', async () => {
        const user = userEvent.setup();
        replies.POST = [
            {
                status: 409,
                body: { detail: 'Esta conta já tem um cadastro de profissional com outros dados.' },
            },
        ];
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));

        await fillPersonal(user);
        await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Esta conta já tem um cadastro de profissional com outros dados.',
        );
    });

    it('não envia duas vezes enquanto o primeiro envio não termina', async () => {
        const user = userEvent.setup();
        let release!: () => void;
        const slow = new Promise<void>((resolve) => {
            release = resolve;
        });
        const base = vi.mocked(fetch).getMockImplementation()!;
        vi.mocked(fetch).mockImplementation(async (input, init) => {
            if (init?.method === 'POST') await slow;
            return base(input, init);
        });
        renderOnboarding(fakeAuthClient({ user: userWith('client') }));

        await fillPersonal(user);
        const button = screen.getByRole('button', { name: 'Concluir cadastro' });
        await user.click(button);
        await user.click(screen.getByRole('button', { name: 'Enviando…' }));
        expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled();
        release();

        await waitFor(() => expect(posts).toHaveLength(1));
    });

    it('cadastro feito com o papel ainda pendente oferece entrar quando liberar', async () => {
        const user = userEvent.setup();
        replies.GET = [{ status: 200, body: { ...PROFESSIONAL, roleStatus: 'PENDING' } }];
        const client = fakeAuthClient({ user: userWith('client') });
        renderOnboarding(client);

        expect(await screen.findByRole('heading', { name: 'Cadastro recebido' })).toBeInTheDocument();
        expect(screen.getByText(/CREF 012345-G\/SP/)).toBeInTheDocument();

        // O Keycloak ainda não atribuiu: continua na mesma tela.
        await user.click(screen.getByRole('button', { name: 'Entrar na área do profissional' }));
        expect(screen.getByRole('heading', { name: 'Cadastro recebido' })).toBeInTheDocument();

        // Agora atribuiu.
        client.grantRoles('client', 'personal');
        await user.click(screen.getByRole('button', { name: 'Entrar na área do profissional' }));
        expect(await screen.findByRole('heading', { name: 'Área do profissional' })).toBeInTheDocument();
    });

    it('sem conexão para renovar a sessão, avisa e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        replies.GET = [{ status: 200, body: PROFESSIONAL }];
        const client = fakeAuthClient({ user: userWith('client') });
        client.failRefresh('offline');
        renderOnboarding(client);

        await user.click(await screen.findByRole('button', { name: 'Entrar na área do profissional' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão com o FitHub');
        expect(screen.getByRole('button', { name: 'Entrar na área do profissional' })).toBeEnabled();
    });
});
