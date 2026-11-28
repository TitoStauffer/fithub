import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';
import type { WorkoutPlan } from '@/lib/trainingApi';

import { PlanEditorPage } from './PlanEditorPage';
import { PlansSection } from './PlansSection';

const API = 'http://localhost:8083';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PLAN = 'p-1';
const SQUAT = 'e-squat';
const PRESS = 'e-press';
const NOW = new Date(2026, 10, 24, 12);

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const item = (exerciseId: string, extra: Record<string, unknown> = {}) => ({
    exerciseId,
    sets: 3,
    repsMin: 10,
    repsMax: 10,
    loadKg: null,
    restSeconds: null,
    note: null,
    technique: null,
    alternatives: [],
    ...extra,
});

const plan = (extra: Partial<WorkoutPlan> = {}): WorkoutPlan => ({
    id: PLAN,
    clientId: CLIENT,
    status: 'DRAFT',
    version: 1,
    name: 'Pernas',
    startsOn: '2026-11-24',
    validityWeeks: 8,
    expiresOn: '2027-01-19',
    divisions: [{ letter: 'A', name: 'Pernas', items: [item(SQUAT)] }],
    createdAt: '2026-11-24T12:00:00Z',
    updatedAt: '2026-11-24T12:00:00Z',
    alerts: [],
    ...extra,
});

beforeEach(() => {
    replies = new Map();
    requests = [];
    replies.set(`GET /exercises/${SQUAT}`, { status: 200, body: { id: SQUAT, name: 'Agachamento livre' } });
    replies.set(`GET /exercises/${PRESS}`, { status: 200, body: { id: PRESS, name: 'Leg press' } });
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
                        <Route path="/app/clientes/:clientId" element={<PlansSection clientId={CLIENT} />} />
                        <Route
                            path="/app/clientes/:clientId/fichas/nova"
                            element={<PlanEditorPage now={NOW} />}
                        />
                        <Route
                            path="/app/clientes/:clientId/fichas/:planId"
                            element={<PlanEditorPage now={NOW} />}
                        />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('fichas do aluno', () => {
    it('lista as fichas com o estado, a versão e o vencimento', async () => {
        replies.set(`GET /clients/${CLIENT}/plans`, {
            status: 200,
            body: [
                plan({ status: 'PUBLISHED', version: 2 }),
                plan({ id: 'p-0', name: 'Antiga', status: 'REPLACED' }),
            ],
        });
        renderAt(`/app/clientes/${CLIENT}`);

        const section = await screen.findByRole('region', { name: 'Fichas de treino' });
        expect(await within(section).findByRole('link', { name: 'Pernas' })).toHaveAttribute(
            'href',
            `/app/clientes/${CLIENT}/fichas/${PLAN}`,
        );
        expect(section).toHaveTextContent('Publicada · versão 2 · vence em 2027-01-19');
        expect(section).toHaveTextContent('Substituída');
        expect(within(section).getByRole('link', { name: 'Nova ficha' })).toHaveAttribute(
            'href',
            `/app/clientes/${CLIENT}/fichas/nova`,
        );
    });
});

describe('editor de ficha', () => {
    it('cria o rascunho com exercício do catálogo, mostra o alerta e publica com confirmação', async () => {
        const user = userEvent.setup();
        replies.set('GET /exercises?name=agach&size=10', {
            status: 200,
            body: { items: [{ id: SQUAT, name: 'Agachamento livre' }], total: 1, page: 0, size: 10 },
        });
        const alert = {
            divisionPosition: 0,
            divisionLetter: 'A',
            itemPosition: 0,
            exerciseId: SQUAT,
            alternative: false,
            region: 'KNEE' as const,
            restrictionId: 'r-1',
            description: 'Condromalácia patelar.',
        };
        replies.set(`POST /clients/${CLIENT}/plans`, { status: 201, body: plan({ alerts: [alert] }) });
        // Depois de criar, a tela vai para o endereço da ficha e relê.
        replies.set(`GET /plans/${PLAN}`, { status: 200, body: plan({ alerts: [alert] }) });
        replies.set(`GET /clients/${CLIENT}/plans`, { status: 200, body: [] });
        replies.set(`POST /plans/${PLAN}/publish`, {
            status: 200,
            body: plan({ status: 'PUBLISHED', publishedAt: '2026-11-24T13:00:00Z', alerts: [alert] }),
        });
        renderAt(`/app/clientes/${CLIENT}/fichas/nova`);

        await user.type(screen.getByLabelText('Nome da ficha'), 'Pernas');
        await user.type(screen.getByLabelText('Nome da divisão A'), 'Pernas');
        await user.type(screen.getByLabelText('Buscar exercício para o treino A'), 'agach');
        await user.click(screen.getAllByRole('button', { name: 'Buscar' })[0]!);
        await user.click(await screen.findByRole('button', { name: 'Adicionar Agachamento livre' }));
        await user.clear(screen.getByLabelText('Séries de Agachamento livre'));
        await user.type(screen.getByLabelText('Séries de Agachamento livre'), '4');
        await user.type(screen.getByLabelText('Até (faixa) de Agachamento livre'), '12');
        await user.type(screen.getByLabelText('Carga (kg) de Agachamento livre'), '60,5');
        await user.selectOptions(screen.getByLabelText('Técnica de Agachamento livre'), 'DROP_SET');
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        // Alerta, não bloqueio: a ficha foi salva e o alerta aparece no item.
        expect(await screen.findByRole('note')).toHaveTextContent(
            'Atenção: o exercício envolve joelho, região restrita do aluno — Condromalácia patelar.',
        );
        expect(sent(`POST /clients/${CLIENT}/plans`)[0]?.body).toEqual({
            baseVersion: null,
            name: 'Pernas',
            startsOn: '2026-11-24',
            validityWeeks: 8,
            divisions: [
                {
                    name: 'Pernas',
                    items: [
                        {
                            exerciseId: SQUAT,
                            sets: 4,
                            repsMin: 10,
                            repsMax: 12,
                            loadKg: 60.5,
                            restSeconds: null,
                            note: null,
                            technique: 'DROP_SET',
                            alternatives: [],
                        },
                    ],
                },
            ],
        });

        // Depois de criar, a tela vai para o endereço da ficha e busca o nome do exercício antes de mostrar o editor
        // (DEV-0157). Essa busca só sai da tela nova: depois dela, o editor de criação já saiu, e o botão encontrado
        // é o do editor carregado. Sem esperar, o clique às vezes caía na tela em "Carregando a ficha…".
        await waitFor(() => expect(sent(`GET /exercises/${SQUAT}`).length).toBeGreaterThan(0));
        await user.click(await screen.findByRole('button', { name: 'Publicar' }));
        expect(screen.getByRole('group', { name: 'Confirmar publicação' })).toBeInTheDocument();
        expect(sent(`POST /plans/${PLAN}/publish`)).toHaveLength(0);
        await user.click(screen.getByRole('button', { name: 'Confirmar publicação' }));
        await waitFor(() => expect(sent(`POST /plans/${PLAN}/publish`)).toHaveLength(1));
        expect(await screen.findByText('Ficha publicada: o aluno já vê no app.')).toBeInTheDocument();
    });

    it('valida como a API antes de enviar e mostra no item o erro que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set(`GET /plans/${PLAN}`, { status: 200, body: plan() });
        replies.set(`PUT /plans/${PLAN}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [
                    {
                        field: 'divisions[0].items[0].exerciseId',
                        message:
                            'Exercício indisponível: ele não existe, foi arquivado ou é de outro personal.',
                    },
                ],
            },
        });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        const sets = await screen.findByLabelText('Séries de Agachamento livre');
        await user.clear(screen.getByLabelText('Nome da ficha'));
        await user.type(screen.getByLabelText('Nome da ficha'), 'P');
        await user.clear(sets);
        await user.type(sets, '0');
        await user.type(screen.getByLabelText('Até (faixa) de Agachamento livre'), '8');
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        expect(screen.getByText('O nome da ficha precisa ter de 2 a 80 caracteres.')).toBeInTheDocument();
        expect(screen.getByText('As séries vão de 1 a 20.')).toBeInTheDocument();
        expect(screen.getByText('O máximo da faixa vai do mínimo até 100.')).toBeInTheDocument();
        expect(sets).toHaveAttribute('aria-invalid', 'true');
        expect(sent(`PUT /plans/${PLAN}`)).toHaveLength(0);

        await user.type(screen.getByLabelText('Nome da ficha'), 'ernas');
        await user.clear(sets);
        await user.type(sets, '3');
        await user.clear(screen.getByLabelText('Até (faixa) de Agachamento livre'));
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        expect(
            await screen.findByText(
                'Exercício indisponível: ele não existe, foi arquivado ou é de outro personal.',
            ),
        ).toBeInTheDocument();
    });

    it('não publica com divisão sem exercício', async () => {
        const user = userEvent.setup();
        replies.set(`GET /plans/${PLAN}`, {
            status: 200,
            body: plan({
                divisions: [
                    { letter: 'A', items: [item(SQUAT)] },
                    { letter: 'B', items: [] },
                ],
            }),
        });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        await user.click(await screen.findByRole('button', { name: 'Publicar' }));

        expect(screen.getByText('Cada divisão precisa de ao menos um exercício.')).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Confirmar publicação' })).not.toBeInTheDocument();
    });

    it('editar a publicada avisa da nova versão, manda a versão base e recarrega num conflito', async () => {
        const user = userEvent.setup();
        replies.set(`GET /plans/${PLAN}`, { status: 200, body: plan({ status: 'PUBLISHED' }) });
        replies.set(`PUT /plans/${PLAN}`, { status: 409, body: { detail: 'A ficha mudou.' } });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        expect(await screen.findByText(/salvar cria a versão 2/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Publicar' })).not.toBeInTheDocument();
        await user.clear(screen.getByLabelText('Nome da ficha'));
        await user.type(screen.getByLabelText('Nome da ficha'), 'Pernas pesado');

        replies.set(`GET /plans/${PLAN}`, {
            status: 200,
            body: plan({ status: 'PUBLISHED', version: 2, name: 'Pernas do colega' }),
        });
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        expect(sent(`PUT /plans/${PLAN}`)[0]?.body).toMatchObject({ baseVersion: 1, name: 'Pernas pesado' });
        expect(await screen.findByDisplayValue('Pernas do colega')).toBeInTheDocument();
        expect(screen.getByText(/salvar cria a versão 3/)).toBeInTheDocument();
    });

    // DEV-0102: o 403 do editor é da assinatura vencida (a rota já exige o papel de personal).
    it('assinatura vencida ao salvar mostra o aviso de renovação com o caminho para a assinatura', async () => {
        const user = userEvent.setup();
        replies.set(`GET /plans/${PLAN}`, { status: 200, body: plan() });
        replies.set(`PUT /plans/${PLAN}`, {
            status: 403,
            body: { title: 'Assinatura vencida', detail: 'Sem assinatura.' },
        });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        await user.type(await screen.findByLabelText('Nome da ficha'), ' B');
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        const notice = await screen.findByText(/assinatura venceu/);
        expect(notice).toHaveAttribute('role', 'alert');
        expect(screen.getByRole('link', { name: 'Renovar a assinatura' })).toHaveAttribute(
            'href',
            '/app/assinatura',
        );
    });

    it('vínculo encerrado mostra que o acesso acabou', async () => {
        replies.set(`GET /plans/${PLAN}`, { status: 404, body: { detail: 'Ficha não encontrada.' } });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        expect(await screen.findByRole('alert')).toHaveTextContent('O acesso a esta ficha acabou');
    });

    it('ordena os itens, troca alternativas e remove', async () => {
        const user = userEvent.setup();
        replies.set(`GET /plans/${PLAN}`, {
            status: 200,
            body: plan({
                divisions: [{ letter: 'A', items: [item(SQUAT, { alternatives: [PRESS] }), item(PRESS)] }],
            }),
        });
        replies.set(`PUT /plans/${PLAN}`, { status: 200, body: plan() });
        replies.set(`GET /clients/${CLIENT}/plans`, { status: 200, body: [] });
        renderAt(`/app/clientes/${CLIENT}/fichas/${PLAN}`);

        const squat = await screen.findByRole('article', { name: 'Agachamento livre' });
        expect(squat).toHaveTextContent('Leg press');
        await user.click(within(squat).getByRole('button', { name: 'Remover a alternativa Leg press' }));
        await user.click(within(squat).getByRole('button', { name: 'Descer' }));
        await user.click(screen.getByRole('button', { name: 'Salvar ficha' }));

        await waitFor(() => expect(sent(`PUT /plans/${PLAN}`)).toHaveLength(1));
        const body = sent(`PUT /plans/${PLAN}`)[0]?.body as {
            divisions: { items: { exerciseId: string; alternatives: string[] }[] }[];
        };
        expect(body.divisions[0]?.items.map((it) => it.exerciseId)).toEqual([PRESS, SQUAT]);
        expect(body.divisions[0]?.items[1]?.alternatives).toEqual([]);
    });
});
