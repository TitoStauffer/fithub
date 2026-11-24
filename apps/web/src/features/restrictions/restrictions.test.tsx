import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';
import { ExpiringPlansPage } from '@/features/plans/ExpiringPlansPage';

import { RestrictionsSection } from './RestrictionsSection';

const API = 'http://localhost:8083';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PATH = `/clients/${CLIENT}/restrictions`;
const NOW = new Date(2026, 10, 24, 12);

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const knee = {
    id: 'r-1',
    region: 'KNEE',
    description: 'Condromalácia patelar.',
    createdAt: '2026-11-24T12:00:00Z',
    updatedAt: '2026-11-24T12:00:00Z',
};

beforeEach(() => {
    replies = new Map();
    requests = [];
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            const key = `${init?.method ?? 'GET'} ${url.replace(API, '')}`;
            requests.push({
                key,
                body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
            });
            const reply = replies.get(key);
            if (!reply) return Promise.reject(new Error(`rota inesperada: ${key}`));
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

function renderAt(path: string) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith('personal') })}>
                <MemoryRouter initialEntries={[path]}>
                    <Routes>
                        <Route
                            path="/app/clientes/:clientId"
                            element={<RestrictionsSection clientId={CLIENT} />}
                        />
                        <Route path="/app/fichas-vencendo" element={<ExpiringPlansPage now={NOW} />} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('restrições de mobilidade', () => {
    it('adiciona com a validação espelhada e mostra na lista', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        replies.set(`POST ${PATH}`, { status: 201, body: knee });
        renderAt(`/app/clientes/${CLIENT}`);

        expect(await screen.findByText('Nenhuma restrição registrada.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Adicionar restrição' }));
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));
        expect(screen.getByText('Informe a região do corpo.')).toBeInTheDocument();
        expect(screen.getByText('A descrição precisa ter de 1 a 500 caracteres.')).toBeInTheDocument();
        expect(sent(`POST ${PATH}`)).toHaveLength(0);

        await user.selectOptions(screen.getByLabelText('Região do corpo'), 'KNEE');
        await user.type(screen.getByLabelText('Descrição'), '  Condromalácia patelar.  ');
        replies.set(`GET ${PATH}`, { status: 200, body: [knee] });
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        expect(await screen.findByRole('article', { name: 'Restrição: joelho' })).toHaveTextContent(
            'joelho — Condromalácia patelar.',
        );
        expect(sent(`POST ${PATH}`)[0]?.body).toEqual({
            region: 'KNEE',
            description: 'Condromalácia patelar.',
        });
    });

    it('mostra no campo o erro que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        replies.set(`POST ${PATH}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'description', message: 'A descrição precisa ter de 1 a 500 caracteres.' }],
            },
        });
        renderAt(`/app/clientes/${CLIENT}`);

        await user.click(await screen.findByRole('button', { name: 'Adicionar restrição' }));
        await user.selectOptions(screen.getByLabelText('Região do corpo'), 'HIP');
        await user.type(screen.getByLabelText('Descrição'), 'Quadril.');
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        expect(await screen.findByText('A descrição precisa ter de 1 a 500 caracteres.')).toBeInTheDocument();
        expect(screen.getByLabelText('Descrição')).toHaveAttribute('aria-invalid', 'true');
    });

    it('edita e remove só depois da confirmação', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [knee] });
        replies.set(`PUT ${PATH}/r-1`, { status: 200, body: { ...knee, description: 'Dor ao agachar.' } });
        replies.set(`DELETE ${PATH}/r-1`, { status: 204 });
        renderAt(`/app/clientes/${CLIENT}`);

        const item = await screen.findByRole('article', { name: 'Restrição: joelho' });
        await user.click(within(item).getByRole('button', { name: 'Editar' }));
        expect(screen.getByLabelText('Região do corpo')).toHaveValue('KNEE');
        await user.clear(screen.getByLabelText('Descrição'));
        await user.type(screen.getByLabelText('Descrição'), 'Dor ao agachar.');
        replies.set(`GET ${PATH}`, { status: 200, body: [{ ...knee, description: 'Dor ao agachar.' }] });
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));
        expect(await screen.findByText(/Dor ao agachar\./)).toBeInTheDocument();
        expect(sent(`PUT ${PATH}/r-1`)[0]?.body).toEqual({ region: 'KNEE', description: 'Dor ao agachar.' });

        const edited = screen.getByRole('article', { name: 'Restrição: joelho' });
        await user.click(within(edited).getByRole('button', { name: 'Remover' }));
        await user.click(within(edited).getByRole('button', { name: 'Cancelar' }));
        expect(sent(`DELETE ${PATH}/r-1`)).toHaveLength(0);

        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        await user.click(within(edited).getByRole('button', { name: 'Remover' }));
        await user.click(within(edited).getByRole('button', { name: 'Confirmar remoção' }));
        await waitFor(() => expect(sent(`DELETE ${PATH}/r-1`)).toHaveLength(1));
        expect(await screen.findByText('Nenhuma restrição registrada.')).toBeInTheDocument();
    });
});

describe('fichas vencendo', () => {
    it('lista aluno, ficha e vencimento, marcando as vencidas', async () => {
        replies.set('GET /plans/expiring', {
            status: 200,
            body: [
                {
                    planId: 'p-1',
                    clientId: 'c-1',
                    clientName: 'Ana Aluna',
                    name: 'Pernas',
                    expiresOn: '2026-11-17',
                },
                {
                    planId: 'p-2',
                    clientId: 'c-2',
                    clientName: null,
                    name: 'Superior',
                    expiresOn: '2026-11-27',
                },
            ],
        });
        renderAt('/app/fichas-vencendo');

        const ana = await screen.findByRole('link', { name: 'Ana Aluna' });
        expect(ana).toHaveAttribute('href', '/app/clientes/c-1');
        const first = ana.closest('li');
        expect(first).toHaveTextContent('Pernas');
        expect(first).toHaveTextContent('venceu em 2026-11-17');
        expect(first).toHaveTextContent('Vencida');
        const second = screen.getByRole('link', { name: 'Aluno' }).closest('li');
        expect(second).toHaveTextContent('Superior');
        expect(second).toHaveTextContent('vence em 2026-11-27');
        expect(second).not.toHaveTextContent('Vencida');
    });

    it('avisa quando não há ficha vencendo e quando a busca falha', async () => {
        const user = userEvent.setup();
        replies.set('GET /plans/expiring', {
            status: 503,
            body: { detail: 'Serviço de treino indisponível.' },
        });
        renderAt('/app/fichas-vencendo');

        expect(screen.getByRole('status')).toHaveTextContent('Carregando…');
        expect(await screen.findByRole('alert')).toHaveTextContent('Serviço de treino indisponível.');
        replies.set('GET /plans/expiring', { status: 200, body: [] });
        await user.click(screen.getByRole('button', { name: 'Tentar de novo' }));
        expect(await screen.findByText('Nenhuma ficha vencendo agora.')).toBeInTheDocument();
    });
});
