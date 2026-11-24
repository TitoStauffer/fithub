import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { AssessmentFormPage } from './AssessmentFormPage';
import { AssessmentHistory } from './AssessmentHistory';

const API = 'http://localhost:8082';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PATH = `/professionals/me/clients/${CLIENT}/assessments`;
const NOW = new Date(2026, 10, 23, 12);

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let posted: unknown[];

beforeEach(() => {
    replies = new Map();
    posted = [];
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            const key = `${init?.method ?? 'GET'} ${url.replace(API, '')}`;
            if (init?.method === 'POST' && typeof init.body === 'string') posted.push(JSON.parse(init.body));
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
                            element={<AssessmentHistory clientId={CLIENT} />}
                        />
                        <Route
                            path="/app/clientes/:clientId/avaliacoes/nova"
                            element={<AssessmentFormPage now={NOW} />}
                        />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

describe('avaliação presencial', () => {
    it('registra com dobras e volta para o cliente', async () => {
        const user = userEvent.setup();
        replies.set(`POST ${PATH}`, { status: 201, body: { id: 'a1' } });
        replies.set(`GET ${PATH}`, { status: 200, body: [] });
        renderAt(`/app/clientes/${CLIENT}/avaliacoes/nova`);

        await user.type(screen.getByLabelText('Data da medição'), '2026-11-20');
        await user.type(screen.getByLabelText('Peso (kg)'), '64,5');
        await user.selectOptions(screen.getByLabelText('Sexo para as fórmulas'), 'FEMALE');
        await user.type(screen.getByLabelText('Cintura'), '70.5');
        await user.type(screen.getByLabelText('Dobra tríceps'), '18');
        await user.click(screen.getByRole('button', { name: 'Salvar avaliação' }));

        expect(await screen.findByText('Nova avaliação presencial', { selector: 'a' })).toBeInTheDocument();
        expect(posted[0]).toEqual({
            measuredOn: '2026-11-20',
            weightKg: 64.5,
            sexForFormulas: 'FEMALE',
            circumferences: { waist: 70.5 },
            skinfolds: { triceps: 18 },
        });
    });

    it('valida no navegador com as regras da API antes de enviar', async () => {
        const user = userEvent.setup();
        renderAt(`/app/clientes/${CLIENT}/avaliacoes/nova`);

        await user.type(screen.getByLabelText('Data da medição'), '2026-11-24');
        await user.type(screen.getByLabelText('Peso (kg)'), '500');
        await user.type(screen.getByLabelText('Dobra coxa'), '90');
        await user.click(screen.getByRole('button', { name: 'Salvar avaliação' }));

        expect(screen.getByText('A data da medição não pode estar no futuro.')).toBeInTheDocument();
        expect(screen.getByText('O peso vai de 20 a 400 kg, com até 1 casa decimal.')).toBeInTheDocument();
        expect(screen.getByText('De 1 a 80 mm, com até 1 casa decimal.')).toBeInTheDocument();
        expect(posted).toHaveLength(0);
    });

    it('erro de campo da API aparece no campo, sem perder o que foi digitado', async () => {
        const user = userEvent.setup();
        replies.set(`POST ${PATH}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'circumferences.waist', message: 'Cintura recusada.' }],
            },
        });
        renderAt(`/app/clientes/${CLIENT}/avaliacoes/nova`);

        await user.type(screen.getByLabelText('Data da medição'), '2026-11-20');
        await user.type(screen.getByLabelText('Peso (kg)'), '64');
        await user.type(screen.getByLabelText('Cintura'), '70');
        await user.click(screen.getByRole('button', { name: 'Salvar avaliação' }));

        expect(await screen.findByText('Cintura recusada.')).toBeInTheDocument();
        expect(screen.getByLabelText('Peso (kg)')).toHaveValue('64');
    });

    it('com o vínculo encerrado, a tela mostra que o acesso acabou', async () => {
        const user = userEvent.setup();
        replies.set(`POST ${PATH}`, { status: 404, body: { detail: 'Cliente não encontrado.' } });
        renderAt(`/app/clientes/${CLIENT}/avaliacoes/nova`);

        await user.type(screen.getByLabelText('Data da medição'), '2026-11-20');
        await user.type(screen.getByLabelText('Peso (kg)'), '64');
        await user.click(screen.getByRole('button', { name: 'Salvar avaliação' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('O acesso a este cliente acabou');
    });
});

describe('histórico', () => {
    it('mostra cada % de gordura com a origem', async () => {
        replies.set(`GET ${PATH}`, {
            status: 200,
            body: [
                {
                    id: 'a2',
                    type: 'ONLINE',
                    measuredOn: '2026-11-22',
                    weightKg: 64,
                    bodyFat: [{ origin: 'NAVY', percent: 26.1 }],
                },
                {
                    id: 'a1',
                    type: 'PRESENTIAL',
                    measuredOn: '2026-11-20',
                    weightKg: 64.5,
                    bodyFat: [
                        { origin: 'POLLOCK_3', percent: 24.4 },
                        { origin: 'BIOIMPEDANCE', percent: 25 },
                    ],
                },
            ],
        });
        renderAt(`/app/clientes/${CLIENT}`);

        expect(await screen.findByText('% de gordura (Marinha (online)): 26.1%')).toBeInTheDocument();
        expect(screen.getByText('% de gordura (Pollock 3 dobras): 24.4%')).toBeInTheDocument();
        expect(screen.getByText('% de gordura (Bioimpedância): 25%')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByText(/Presencial · 64.5 kg/)).toBeInTheDocument());
    });
});
