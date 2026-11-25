import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { FoodRestrictionsSection } from './FoodRestrictionsSection';
import { termsOf, validate } from './restrictionForm';

const API = 'http://localhost:8084';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PATH = `/clients/${CLIENT}/food-restrictions`;

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const milk = {
    id: 'r-1',
    clientId: CLIENT,
    kind: 'ALLERGY',
    description: 'Alergia à proteína do leite',
    terms: ['leite', 'queijo'],
    createdAt: '2026-11-25T12:00:00Z',
    updatedAt: '2026-11-25T12:00:00Z',
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

function renderSection() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith('nutritionist') })}>
                <MemoryRouter initialEntries={[`/app/clientes/${CLIENT}`]}>
                    <Routes>
                        <Route
                            path="/app/clientes/:clientId"
                            element={<FoodRestrictionsSection clientId={CLIENT} />}
                        />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('restrições alimentares', () => {
    it('adiciona uma alergia com termos, com a validação espelhada, e mostra na lista', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        replies.set(`POST ${PATH}`, { status: 201, body: milk });
        renderSection();

        expect(await screen.findByText('Nenhuma restrição registrada.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Adicionar restrição' }));
        await user.type(screen.getByLabelText('Descrição'), 'A');
        await user.type(screen.getByLabelText('Termos (separados por vírgula)'), 'leite, x, LEITE');
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        expect(screen.getByText('Escolha o tipo.')).toBeInTheDocument();
        expect(screen.getByText('A descrição precisa ter de 2 a 200 caracteres.')).toBeInTheDocument();
        expect(screen.getByText('O termo "x" precisa ter de 2 a 40 caracteres.')).toBeInTheDocument();
        expect(screen.getByText('O termo "LEITE" está repetido.')).toBeInTheDocument();
        expect(screen.getByLabelText('Termos (separados por vírgula)')).toHaveAttribute(
            'aria-invalid',
            'true',
        );
        expect(sent(`POST ${PATH}`)).toHaveLength(0);

        await user.selectOptions(screen.getByLabelText('Tipo'), 'ALLERGY');
        await user.clear(screen.getByLabelText('Descrição'));
        await user.type(screen.getByLabelText('Descrição'), 'Alergia à proteína do leite');
        await user.clear(screen.getByLabelText('Termos (separados por vírgula)'));
        await user.type(screen.getByLabelText('Termos (separados por vírgula)'), ' leite , queijo, ');
        replies.set(`GET ${PATH}`, { status: 200, body: [milk] });
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        await waitFor(() => expect(sent(`POST ${PATH}`)).toHaveLength(1));
        expect(sent(`POST ${PATH}`)[0]?.body).toEqual({
            kind: 'ALLERGY',
            description: 'Alergia à proteína do leite',
            terms: ['leite', 'queijo'],
        });
        const item = await screen.findByRole('article', { name: 'Alergia: Alergia à proteína do leite' });
        expect(item).toHaveTextContent('Termos: leite, queijo');
    });

    it('mostra no campo o erro que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        replies.set(`POST ${PATH}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'terms[1]', message: 'Cada termo tem de 2 a 40 caracteres.' }],
            },
        });
        renderSection();

        await user.click(await screen.findByRole('button', { name: 'Adicionar restrição' }));
        await user.selectOptions(screen.getByLabelText('Tipo'), 'INTOLERANCE');
        await user.type(screen.getByLabelText('Descrição'), 'Intolerância à lactose');
        await user.type(screen.getByLabelText('Termos (separados por vírgula)'), 'lactose, leite');
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        expect(await screen.findByText('Cada termo tem de 2 a 40 caracteres.')).toBeInTheDocument();
        expect(screen.getByLabelText('Termos (separados por vírgula)')).toHaveAttribute(
            'aria-invalid',
            'true',
        );
    });

    it('mostra o limite de 30 restrições que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        replies.set(`POST ${PATH}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'restrictions', message: 'O paciente tem até 30 restrições.' }],
            },
        });
        renderSection();

        await user.click(await screen.findByRole('button', { name: 'Adicionar restrição' }));
        await user.selectOptions(screen.getByLabelText('Tipo'), 'PREFERENCE');
        await user.type(screen.getByLabelText('Descrição'), 'Não gosta de peixe');
        await user.type(screen.getByLabelText('Termos (separados por vírgula)'), 'peixe');
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));

        expect(await screen.findByText('O paciente tem até 30 restrições.')).toBeInTheDocument();
    });

    it('edita e apaga só depois da confirmação', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${PATH}`, { status: 200, body: [milk] });
        replies.set(`PUT ${PATH}/r-1`, { status: 200, body: { ...milk, kind: 'INTOLERANCE' } });
        replies.set(`DELETE ${PATH}/r-1`, { status: 204 });
        renderSection();

        const item = await screen.findByRole('article', { name: 'Alergia: Alergia à proteína do leite' });
        await user.click(within(item).getByRole('button', { name: 'Editar' }));
        expect(screen.getByLabelText('Termos (separados por vírgula)')).toHaveValue('leite, queijo');
        await user.selectOptions(screen.getByLabelText('Tipo'), 'INTOLERANCE');
        await user.click(screen.getByRole('button', { name: 'Salvar restrição' }));
        await waitFor(() => expect(sent(`PUT ${PATH}/r-1`)).toHaveLength(1));
        expect(sent(`PUT ${PATH}/r-1`)[0]?.body).toEqual({
            kind: 'INTOLERANCE',
            description: 'Alergia à proteína do leite',
            terms: ['leite', 'queijo'],
        });

        const again = await screen.findByRole('article', { name: 'Alergia: Alergia à proteína do leite' });
        await user.click(within(again).getByRole('button', { name: 'Apagar' }));
        expect(sent(`DELETE ${PATH}/r-1`)).toHaveLength(0);
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        await user.click(within(again).getByRole('button', { name: 'Confirmar exclusão' }));
        await waitFor(() => expect(sent(`DELETE ${PATH}/r-1`)).toHaveLength(1));
        expect(await screen.findByText('Nenhuma restrição registrada.')).toBeInTheDocument();
    });
});

describe('validação da restrição', () => {
    const form = { kind: 'ALLERGY' as const, description: 'Leite', terms: 'leite' };

    it('espelha os limites da API', () => {
        expect(validate(form)).toEqual({});
        expect(validate({ ...form, description: 'a'.repeat(201) })).toHaveProperty('description');
        expect(validate({ ...form, terms: ' , ' })).toHaveProperty('terms');
        const eleven = Array.from({ length: 11 }, (_, index) => `termo${index}`).join(',');
        expect(validate({ ...form, terms: eleven })).toHaveProperty('terms');
        expect(validate({ ...form, terms: `leite, ${'a'.repeat(41)}` })).toHaveProperty(['terms[1]']);
        expect(termsOf(' leite , , queijo ')).toEqual(['leite', 'queijo']);
    });
});
