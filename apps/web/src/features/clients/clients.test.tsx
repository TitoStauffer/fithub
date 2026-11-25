import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { ClientDetailPage } from './ClientDetailPage';
import { ClientsPage } from './ClientsPage';
import { InvitationsPage } from './InvitationsPage';

// As seções de fichas e de restrições têm os testes delas (plans.test.tsx e restrictions.test.tsx).
vi.mock('@/features/plans/PlansSection', () => ({ PlansSection: () => null }));
vi.mock('@/features/restrictions/RestrictionsSection', () => ({ RestrictionsSection: () => null }));
// A evolução tem os testes dela (progress.test.tsx); aqui só importa quem vê a seção.
vi.mock('@/features/progress/ClientProgressSection', () => ({
    ClientProgressSection: () => <section aria-label="Evolução" />,
}));
vi.mock('@/features/progress/PatientProgressSection', () => ({
    PatientProgressSection: () => <section aria-label="Evolução da dieta" />,
}));

const API = 'http://localhost:8082';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const LINK = '0193a1b2-0000-7000-8000-0000000000aa';

type Reply = { status: number; body?: unknown };
type Route = { method: string; path: string };
let routes: Map<string, Reply[]>;
let calls: { method: string; path: string; body?: unknown }[];

function reply(method: string, path: string, ...replies: Reply[]) {
    routes.set(`${method} ${path}`, replies);
}

beforeEach(() => {
    routes = new Map();
    calls = [];
    // Histórico de avaliações da tela do cliente (DEV-0049); os testes dele estão em assessments.test.tsx.
    routes.set(`GET /professionals/me/clients/${CLIENT}/assessments`, [{ status: 200, body: [] }]);
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            const route: Route = { method: init?.method ?? 'GET', path: url.replace(API, '') };
            calls.push({
                ...route,
                body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
            });
            const queue = routes.get(`${route.method} ${route.path}`);
            if (!queue) return Promise.reject(new Error(`rota inesperada: ${route.method} ${route.path}`));
            const next = queue.length > 1 ? queue.shift()! : queue[0]!;
            return Promise.resolve(
                new Response(next.body === undefined ? null : JSON.stringify(next.body), {
                    status: next.status,
                    headers: { 'Content-Type': 'application/json' },
                }),
            );
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
});

function renderAt(path: string, role: 'personal' | 'nutritionist' = 'personal') {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith(role) })}>
                <MemoryRouter initialEntries={[path]}>
                    <Routes>
                        <Route path="/app" element={<ClientsPage />} />
                        <Route path="/app/convites" element={<InvitationsPage />} />
                        <Route path="/app/clientes/:clientId" element={<ClientDetailPage />} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const CLIENTS = [
    { linkId: LINK, clientId: CLIENT, clientName: 'Ana Cliente', startedAt: '2026-11-23T12:00:00Z' },
];
const PROFILE = {
    name: 'Ana Cliente',
    birthDate: '1990-11-24',
    age: 35,
    gender: 'FEMALE',
    heightCm: 165,
    goal: 'HYPERTROPHY',
};
const HEALTH = { conditions: ['HYPERTENSION'], notes: 'joelho', updatedAt: '2026-11-23T12:00:00Z' };

describe('convites', () => {
    beforeEach(() => {
        reply('GET', '/invitations', { status: 200, body: [] });
        reply('GET', '/professionals/me/invite-code', { status: 200, body: { code: 'ABCD2345' } });
    });

    it('convida por e-mail e mostra o link e o código', async () => {
        const user = userEvent.setup();
        reply('POST', '/invitations', {
            status: 201,
            body: {
                id: 'i1',
                email: 'ana@exemplo.com',
                link: 'fithub://convite/token',
                code: 'WXYZ6789',
                expiresAt: '2026-11-30T12:00:00Z',
                emailSent: true,
            },
        });
        renderAt('/app/convites');

        await user.type(await screen.findByLabelText('E-mail do cliente'), 'ana@exemplo.com');
        await user.click(screen.getByRole('button', { name: 'Convidar por e-mail' }));

        expect(await screen.findByText('Convite enviado para ana@exemplo.com.')).toBeInTheDocument();
        expect(screen.getByText('WXYZ6789')).toBeInTheDocument();
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ email: 'ana@exemplo.com' });
    });

    it('gera link e código para compartilhar, sem e-mail', async () => {
        const user = userEvent.setup();
        reply('POST', '/invitations', {
            status: 201,
            body: { id: 'i2', email: null, link: 'fithub://convite/t2', code: 'QRST2345', emailSent: false },
        });
        renderAt('/app/convites');

        await user.click(await screen.findByRole('button', { name: 'Gerar link e código' }));

        expect(await screen.findByText('Compartilhe o link ou o código com o cliente.')).toBeInTheDocument();
        expect(screen.getByText('fithub://convite/t2')).toBeInTheDocument();
        expect(calls.find((call) => call.method === 'POST')?.body).toEqual({});
    });

    it('e-mail inválido não é enviado', async () => {
        const user = userEvent.setup();
        renderAt('/app/convites');

        await user.type(await screen.findByLabelText('E-mail do cliente'), 'sem-arroba');
        await user.click(screen.getByRole('button', { name: 'Convidar por e-mail' }));

        expect(await screen.findByText('Informe um e-mail válido.')).toBeInTheDocument();
        expect(calls.some((call) => call.method === 'POST')).toBe(false);
    });

    it('lista e cancela os convites pendentes', async () => {
        const user = userEvent.setup();
        reply(
            'GET',
            '/invitations',
            {
                status: 200,
                body: [
                    { id: 'i9', email: 'bia@exemplo.com', state: 'PENDING' },
                    { id: 'i8', state: 'CANCELLED' },
                ],
            },
            { status: 200, body: [] },
        );
        reply('DELETE', '/invitations/i9', { status: 204 });
        renderAt('/app/convites');

        const cancelBia = await screen.findByRole('button', { name: 'Cancelar convite bia@exemplo.com' });
        // Só os pendentes aparecem: o cancelado não tem botão.
        expect(screen.getAllByRole('button', { name: /Cancelar convite/ })).toHaveLength(1);
        await user.click(cancelBia);

        await waitFor(() => expect(calls.some((call) => call.method === 'DELETE')).toBe(true));
        expect(await screen.findByText('Nenhum convite pendente.')).toBeInTheDocument();
    });

    it('mostra e troca o código pessoal', async () => {
        const user = userEvent.setup();
        reply('POST', '/professionals/me/invite-code', { status: 200, body: { code: 'NEWC2345' } });
        renderAt('/app/convites');

        expect(await screen.findByLabelText('Código pessoal')).toHaveTextContent('ABCD2345');
        await user.click(screen.getByRole('button', { name: 'Trocar código' }));

        await waitFor(() => expect(screen.getByLabelText('Código pessoal')).toHaveTextContent('NEWC2345'));
    });
});

describe('clientes', () => {
    it('lista os clientes com vínculo ativo', async () => {
        reply('GET', '/professionals/me/clients', { status: 200, body: CLIENTS });
        renderAt('/app');

        expect(await screen.findByRole('link', { name: 'Ana Cliente' })).toHaveAttribute(
            'href',
            `/app/clientes/${CLIENT}`,
        );
    });

    it('a tela do cliente mostra o perfil e a saúde vindos da API', async () => {
        reply('GET', '/professionals/me/clients', { status: 200, body: CLIENTS });
        reply('GET', `/professionals/me/clients/${CLIENT}/profile`, { status: 200, body: PROFILE });
        reply('GET', `/professionals/me/clients/${CLIENT}/health`, { status: 200, body: HEALTH });
        renderAt(`/app/clientes/${CLIENT}`);

        expect(await screen.findByText('35 anos')).toBeInTheDocument();
        expect(screen.getByText('Objetivo: Hipertrofia')).toBeInTheDocument();
        expect(await screen.findByText('Condições: Hipertensão')).toBeInTheDocument();
        expect(screen.getByText('Observações: joelho')).toBeInTheDocument();
        expect(screen.getByRole('region', { name: 'Evolução' })).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Evolução da dieta' })).not.toBeInTheDocument();
        // As restrições alimentares e os planos alimentares são só da nutricionista (BR-03).
        expect(screen.queryByRole('region', { name: 'Restrições alimentares' })).not.toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Planos alimentares' })).not.toBeInTheDocument();
    });

    it('a nutricionista vê as restrições alimentares e os planos alimentares do paciente', async () => {
        reply('GET', '/professionals/me/clients', { status: 200, body: CLIENTS });
        reply('GET', `/professionals/me/clients/${CLIENT}/profile`, { status: 200, body: PROFILE });
        reply('GET', `/professionals/me/clients/${CLIENT}/health`, { status: 200, body: HEALTH });
        renderAt(`/app/clientes/${CLIENT}`, 'nutritionist');

        expect(await screen.findByRole('region', { name: 'Restrições alimentares' })).toBeInTheDocument();
        expect(screen.getByRole('region', { name: 'Planos alimentares' })).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Restrições de mobilidade' })).not.toBeInTheDocument();
        // A evolução do treino é do personal (DEV-0094).
        expect(screen.queryByRole('region', { name: 'Evolução' })).not.toBeInTheDocument();
        // A evolução da dieta é da nutricionista (DEV-0095).
        expect(screen.getByRole('region', { name: 'Evolução da dieta' })).toBeInTheDocument();
    });

    it('cliente que ainda não informou a saúde não é confundido com acesso encerrado', async () => {
        reply('GET', '/professionals/me/clients', { status: 200, body: CLIENTS });
        reply('GET', `/professionals/me/clients/${CLIENT}/profile`, { status: 200, body: PROFILE });
        reply('GET', `/professionals/me/clients/${CLIENT}/health`, {
            status: 404,
            body: { detail: 'O cliente ainda não informou os dados de saúde.' },
        });
        renderAt(`/app/clientes/${CLIENT}`);

        expect(
            await screen.findByText('O cliente ainda não informou os dados de saúde.'),
        ).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('com o vínculo encerrado, a tela mostra que o acesso acabou', async () => {
        reply('GET', '/professionals/me/clients', { status: 200, body: [] });
        reply('GET', `/professionals/me/clients/${CLIENT}/profile`, {
            status: 404,
            body: { detail: 'Cliente não encontrado.' },
        });
        renderAt(`/app/clientes/${CLIENT}`);

        expect(await screen.findByRole('alert')).toHaveTextContent('O acesso a este cliente acabou');
    });

    it('encerrar o vínculo pede confirmação', async () => {
        const user = userEvent.setup();
        reply('GET', '/professionals/me/clients', { status: 200, body: CLIENTS }, { status: 200, body: [] });
        reply('GET', `/professionals/me/clients/${CLIENT}/profile`, { status: 200, body: PROFILE });
        reply('GET', `/professionals/me/clients/${CLIENT}/health`, { status: 200, body: HEALTH });
        reply('POST', `/links/${LINK}/end`, { status: 200, body: { id: LINK, status: 'ENDED' } });
        renderAt(`/app/clientes/${CLIENT}`);

        await user.click(await screen.findByRole('button', { name: 'Encerrar vínculo' }));
        expect(calls.some((call) => call.path.endsWith('/end'))).toBe(false);
        await user.click(screen.getByRole('button', { name: 'Cancelar' }));
        expect(calls.some((call) => call.path.endsWith('/end'))).toBe(false);

        await user.click(screen.getByRole('button', { name: 'Encerrar vínculo' }));
        await user.click(screen.getByRole('button', { name: 'Confirmar encerramento' }));

        expect(await screen.findByText('Vínculo encerrado.')).toBeInTheDocument();
        expect(calls.some((call) => call.method === 'POST' && call.path === `/links/${LINK}/end`)).toBe(true);
    });
});
