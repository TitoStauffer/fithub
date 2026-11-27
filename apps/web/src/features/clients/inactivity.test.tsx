import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { ClientsPage } from './ClientsPage';
import { activityOf, daysSince, inactiveThreshold, latestActivity } from './inactivity';

const NOW = new Date(2026, 10, 25, 12);
const CLIENTS_URL = 'http://localhost:8082/professionals/me/clients';
const TRAINING = 'http://localhost:8083/professionals/me/clients/activity';
const NUTRITION = 'http://localhost:8084/professionals/me/clients/activity';

const CLIENTS = [
    { linkId: 'l1', clientId: 'c1', clientName: 'Ana', startedAt: '2026-10-01T12:00:00Z' },
    { linkId: 'l2', clientId: 'c2', clientName: 'Bruno', startedAt: '2026-10-01T12:00:00Z' },
    { linkId: 'l3', clientId: 'c3', clientName: 'Carla', startedAt: '2026-10-01T12:00:00Z' },
];

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requested: string[];

beforeEach(() => {
    replies = new Map();
    requested = [];
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            requested.push(url);
            const reply = replies.get(url);
            if (!reply) return Promise.reject(new Error(`rota inesperada: ${url}`));
            return Promise.resolve(
                new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
                    status: reply.status,
                    headers: { 'Content-Type': 'application/json' },
                }),
            );
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
});

/** Mostra a busca da URL atual, para conferir que o limite fica nela. */
function Search() {
    return <output aria-label="busca">{useLocation().search}</output>;
}

function renderAt(path: string, role: 'personal' | 'nutritionist' = 'personal') {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith(role) })}>
                <MemoryRouter initialEntries={[path]}>
                    <Routes>
                        <Route
                            path="/app"
                            element={
                                <>
                                    <ClientsPage now={NOW} />
                                    <Search />
                                    {/* Muda a URL na mesma rota, como voltar no navegador ou seguir um link. */}
                                    <Link to="/app?inativos=14">limite de 14 dias</Link>
                                </>
                            }
                        />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

function item(name: string) {
    return screen.getByRole('link', { name }).closest('li')!;
}

describe('clientes inativos (DEV-0097)', () => {
    it('o personal vê quem passou do limite pela atividade de treino', async () => {
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, {
            status: 200,
            body: [
                { clientId: 'c1', lastActivityAt: '2026-11-24T20:00:00Z' },
                { clientId: 'c2', lastActivityAt: '2026-11-10T12:00:00Z' },
                { clientId: 'c3' },
            ],
        });
        renderAt('/app');

        expect(
            await within(await screen.findByRole('list')).findByText(/Inativo há 15 dias/),
        ).toBeInTheDocument();
        expect(item('Ana')).toHaveTextContent('Última atividade em 24/11/2026');
        expect(item('Bruno')).toHaveTextContent('Inativo há 15 dias');
        expect(item('Carla')).toHaveTextContent('Sem atividade registrada');
        // A nutricionista não é lida para o personal.
        expect(requested).not.toContain(NUTRITION);
    });

    it('o limite muda pela tela e fica na URL', async () => {
        const user = userEvent.setup();
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, {
            status: 200,
            body: [
                { clientId: 'c1', lastActivityAt: '2026-11-24T20:00:00Z' },
                { clientId: 'c2', lastActivityAt: '2026-11-10T12:00:00Z' },
            ],
        });
        renderAt('/app?inativos=20');

        await screen.findByText(/Sem atividade registrada/);
        expect(screen.getByLabelText('Inativo depois de (dias)')).toHaveValue(20);
        expect(item('Bruno')).toHaveTextContent('Última atividade em 10/11/2026');

        const input = screen.getByLabelText('Inativo depois de (dias)');
        // Apagar para digitar não volta ao padrão: o limite anterior vale até o novo ser válido.
        await user.clear(input);
        expect(input).toHaveValue(null);
        expect(item('Bruno')).toHaveTextContent('Última atividade em 10/11/2026');
        await user.type(input, '16');
        expect(item('Bruno')).toHaveTextContent('Última atividade em 10/11/2026');
        await user.clear(input);
        await user.type(input, '14');
        expect(input).toHaveValue(14);
        expect(item('Bruno')).toHaveTextContent('Inativo há 15 dias');
        expect(screen.getByRole('status', { name: 'busca' })).toHaveTextContent('?inativos=14');
    });

    it('o filtro mostra só os inativos', async () => {
        const user = userEvent.setup();
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, {
            status: 200,
            body: [
                { clientId: 'c1', lastActivityAt: '2026-11-24T20:00:00Z' },
                { clientId: 'c2', lastActivityAt: '2026-11-10T12:00:00Z' },
            ],
        });
        renderAt('/app');
        await screen.findByText(/Inativo há 15 dias/);

        await user.click(screen.getByLabelText('Só os inativos'));
        expect(screen.queryByRole('link', { name: 'Ana' })).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Bruno' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Carla' })).toBeInTheDocument();
    });

    it('a nutricionista vê pela atividade de refeição; com todos ativos, o filtro diz que não há inativos', async () => {
        const user = userEvent.setup();
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS.slice(0, 1) });
        replies.set(NUTRITION, {
            status: 200,
            body: [{ clientId: 'c1', lastActivityAt: '2026-11-25T09:00:00Z' }],
        });
        renderAt('/app', 'nutritionist');

        expect(await screen.findByText(/Última atividade em 25\/11\/2026/)).toBeInTheDocument();
        expect(requested).not.toContain(TRAINING);
        await user.click(screen.getByLabelText('Só os inativos'));
        expect(screen.getByText('Nenhum cliente inativo há mais de 7 dias.')).toBeInTheDocument();
    });

    it('sem a atividade, avisa e não marca ninguém', async () => {
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, { status: 500, body: { detail: 'Falhou.' } });
        renderAt('/app');

        expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível ler a última atividade');
        expect(screen.getByRole('link', { name: 'Carla' })).toBeInTheDocument();
        expect(screen.queryByText(/Sem atividade registrada|Inativo há/)).not.toBeInTheDocument();
    });

    // DEV-0159: com o filtro ligado e a atividade ainda sem resposta (carregando ou com erro), a tela não afirma que
    // não há inativos.
    it('sem a atividade, o filtro não diz que não há inativos', async () => {
        const user = userEvent.setup();
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, { status: 500, body: { detail: 'Falhou.' } });
        renderAt('/app');
        await screen.findByRole('alert');

        await user.click(screen.getByLabelText('Só os inativos'));
        expect(screen.queryByText(/Nenhum cliente inativo/)).not.toBeInTheDocument();
    });

    // DEV-0159: o campo acompanha o limite quando a URL muda sem desmontar a tela.
    it('mudar o limite na URL, na mesma rota, atualiza o campo', async () => {
        const user = userEvent.setup();
        replies.set(CLIENTS_URL, { status: 200, body: CLIENTS });
        replies.set(TRAINING, {
            status: 200,
            body: [{ clientId: 'c2', lastActivityAt: '2026-11-10T12:00:00Z' }],
        });
        renderAt('/app?inativos=20');
        await screen.findAllByText(/Sem atividade registrada/);
        expect(screen.getByLabelText('Inativo depois de (dias)')).toHaveValue(20);

        await user.click(screen.getByRole('link', { name: 'limite de 14 dias' }));
        expect(screen.getByLabelText('Inativo depois de (dias)')).toHaveValue(14);
        expect(item('Bruno')).toHaveTextContent('Inativo há 15 dias');
    });

    it('as regras: limite de 1 a 60, dias inteiros, sem registro conta como inativo, a mais recente vence', () => {
        expect(inactiveThreshold(null)).toBe(7);
        expect(inactiveThreshold('0')).toBe(7);
        expect(inactiveThreshold('1')).toBe(1);
        expect(inactiveThreshold('60')).toBe(60);
        expect(inactiveThreshold('61')).toBe(7);
        expect(inactiveThreshold('2.5')).toBe(7);
        expect(inactiveThreshold('')).toBe(7);
        expect(daysSince(new Date(2026, 10, 18, 23, 59).toISOString(), NOW)).toBe(7);
        expect(activityOf(new Date(2026, 10, 18, 8).toISOString(), NOW, 7).kind).toBe('active');
        expect(activityOf(new Date(2026, 10, 17, 8).toISOString(), NOW, 7)).toEqual({
            kind: 'inactive',
            days: 8,
        });
        expect(activityOf(undefined, NOW, 7)).toEqual({ kind: 'none' });
        expect(
            latestActivity([
                [{ clientId: 'c1', lastActivityAt: '2026-11-01T12:00:00Z' }],
                [{ clientId: 'c1', lastActivityAt: '2026-11-20T12:00:00Z' }, { clientId: 'c2' }],
            ]),
        ).toEqual(new Map([['c1', '2026-11-20T12:00:00Z']]));
    });
});
