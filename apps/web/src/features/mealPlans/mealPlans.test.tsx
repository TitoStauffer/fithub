import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';
import type { MealPlan } from '@/lib/nutritionApi';

import { MealPlanEditorPage } from './MealPlanEditorPage';
import { MealPlansSection } from './MealPlansSection';
import { emptyItem, toInput, validate, type MealPlanForm } from './mealPlanForm';

const API = 'http://localhost:8084';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PLAN = 'mp-1';
const RICE = 'f-rice';
const BEANS = 'f-beans';
const PASTA = 'f-pasta';
const NOW = new Date(2026, 10, 25, 12);

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const amounts = (energyKcal: number, proteinG: number, carbsG = 0, fatG = 0) => ({
    energyKcal,
    proteinG,
    carbsG,
    fatG,
});

const savedItem = (extra: Record<string, unknown> = {}) => ({
    foodId: RICE,
    foodName: 'Arroz, tipo 1, cozido',
    baseUnit: 'G' as const,
    amount: 100,
    nutrients: amounts(128, 2.5, 28.1, 0.2),
    alternatives: [],
    alerts: [],
    ...extra,
});

const plan = (extra: Partial<MealPlan> = {}): MealPlan => ({
    id: PLAN,
    clientId: CLIENT,
    status: 'DRAFT',
    version: 1,
    name: 'Plano de dezembro',
    startsOn: '2026-12-01',
    meals: [
        {
            position: 0,
            name: 'Almoço',
            suggestedTime: '12:30',
            items: [savedItem()],
            total: amounts(128, 2.5, 28.1, 0.2),
        },
    ],
    day: amounts(128, 2.5, 28.1, 0.2),
    createdAt: '2026-11-25T12:00:00Z',
    updatedAt: '2026-11-25T12:00:00Z',
    ...extra,
});

const page = (items: { id: string; name: string }[]) => ({
    items: items.map((food) => ({ ...food, kind: 'TACO', baseUnit: 'G' })),
    page: 0,
    size: 10,
    total: items.length,
});

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
            <AuthProvider client={fakeAuthClient({ user: userWith('nutritionist') })}>
                <MemoryRouter initialEntries={[path]}>
                    <Routes>
                        <Route
                            path="/app/clientes/:clientId"
                            element={<MealPlansSection clientId={CLIENT} />}
                        />
                        <Route
                            path="/app/clientes/:clientId/planos/novo"
                            element={<MealPlanEditorPage now={NOW} />}
                        />
                        <Route
                            path="/app/clientes/:clientId/planos/:planId"
                            element={<MealPlanEditorPage now={NOW} />}
                        />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('planos alimentares do paciente', () => {
    it('lista os planos com o estado, a versão e o início', async () => {
        replies.set(`GET /clients/${CLIENT}/meal-plans`, {
            status: 200,
            body: [
                plan({ status: 'PUBLISHED', version: 2 }),
                plan({ id: 'mp-0', name: 'Antigo', status: 'REPLACED' }),
            ],
        });
        renderAt(`/app/clientes/${CLIENT}`);

        const section = await screen.findByRole('region', { name: 'Planos alimentares' });
        expect(await within(section).findByText('Plano de dezembro')).toBeInTheDocument();
        expect(section).toHaveTextContent('Publicado · versão 2 · início em 2026-12-01');
        expect(section).toHaveTextContent('Substituído');
        expect(within(section).getByRole('link', { name: 'Novo plano' })).toHaveAttribute(
            'href',
            `/app/clientes/${CLIENT}/planos/novo`,
        );
    });

    it('monta o plano com refeição, alimento e alternativa, mostra os macros e o alerta e publica', async () => {
        const user = userEvent.setup();
        replies.set('GET /foods?name=arroz&size=10', {
            status: 200,
            body: page([{ id: RICE, name: 'Arroz, tipo 1, cozido' }]),
        });
        replies.set('GET /foods?name=macarr%C3%A3o&size=10', {
            status: 200,
            body: page([{ id: PASTA, name: 'Macarrão, trigo, cozido' }]),
        });
        const created = plan({
            meals: [
                {
                    position: 0,
                    name: 'Almoço',
                    suggestedTime: '12:30',
                    items: [
                        savedItem({
                            amount: 150,
                            nutrients: amounts(192, 3.8, 42.2, 0.3),
                            alternatives: [
                                {
                                    foodId: PASTA,
                                    foodName: 'Macarrão, trigo, cozido',
                                    baseUnit: 'G',
                                    amount: 80,
                                    nutrients: amounts(81.6, 2.7, 15.9, 1),
                                    difference: amounts(-110.4, -1.1, -26.3, 0.7),
                                    alerts: [
                                        {
                                            restrictionId: 'r-1',
                                            kind: 'ALLERGY',
                                            description: 'Alergia ao trigo',
                                        },
                                    ],
                                },
                            ],
                        }),
                    ],
                    total: amounts(192, 3.8, 42.2, 0.3),
                },
            ],
            day: amounts(192, 3.8, 42.2, 0.3),
        });
        replies.set(`POST /clients/${CLIENT}/meal-plans`, { status: 201, body: created });
        replies.set(`GET /clients/${CLIENT}/meal-plans`, { status: 200, body: [created] });
        replies.set(`GET /meal-plans/${PLAN}`, { status: 200, body: created });
        replies.set(`POST /meal-plans/${PLAN}/publish`, {
            status: 200,
            body: { ...created, status: 'PUBLISHED' },
        });
        renderAt(`/app/clientes/${CLIENT}/planos/novo`);

        await user.type(screen.getByLabelText('Nome do plano'), 'Plano de dezembro');
        await user.type(screen.getByLabelText('Nome da refeição 1'), 'Almoço');
        await user.type(screen.getByLabelText('Horário da refeição 1'), '12:30');
        await user.type(screen.getByLabelText('Buscar alimento para a refeição 1'), 'arroz');
        await user.click(screen.getByRole('button', { name: 'Buscar' }));
        await user.click(await screen.findByRole('button', { name: 'Adicionar Arroz, tipo 1, cozido' }));
        const rice = screen.getByRole('article', { name: 'Arroz, tipo 1, cozido' });
        await user.clear(within(rice).getByLabelText('Quantidade de Arroz, tipo 1, cozido'));
        await user.type(within(rice).getByLabelText('Quantidade de Arroz, tipo 1, cozido'), '150');
        await user.type(
            within(rice).getByLabelText('Buscar alternativa para Arroz, tipo 1, cozido'),
            'macarrão',
        );
        await user.click(within(rice).getByRole('button', { name: 'Buscar' }));
        await user.click(
            await within(rice).findByRole('button', { name: 'Adicionar Macarrão, trigo, cozido' }),
        );
        // O próprio alimento principal não entra como alternativa dele.
        const altSearch = within(rice).getByLabelText('Buscar alternativa para Arroz, tipo 1, cozido');
        await user.clear(altSearch);
        await user.type(altSearch, 'arroz');
        await user.click(within(rice).getByRole('button', { name: 'Buscar' }));
        await user.click(
            await within(rice).findByRole('button', { name: 'Adicionar Arroz, tipo 1, cozido' }),
        );
        await user.clear(within(rice).getByLabelText('Quantidade da alternativa Macarrão, trigo, cozido'));
        await user.type(
            within(rice).getByLabelText('Quantidade da alternativa Macarrão, trigo, cozido'),
            '80',
        );
        await user.click(screen.getByRole('button', { name: 'Salvar plano' }));

        await waitFor(() => expect(sent(`POST /clients/${CLIENT}/meal-plans`)).toHaveLength(1));
        expect(sent(`POST /clients/${CLIENT}/meal-plans`)[0]?.body).toEqual({
            name: 'Plano de dezembro',
            startsOn: '2026-11-25',
            meals: [
                {
                    name: 'Almoço',
                    suggestedTime: '12:30',
                    items: [{ foodId: RICE, amount: 150, alternatives: [{ foodId: PASTA, amount: 80 }] }],
                },
            ],
        });

        // Depois de criar, a tela vai para o endereço do plano e o carrega de novo: espera o editor carregado, senão o
        // texto achado pode ser o do editor de criação, que sai da tela logo depois (aprendizado da DEV-0157).
        await waitFor(() => expect(sent(`GET /meal-plans/${PLAN}`).length).toBeGreaterThan(0));
        const publishButton = await screen.findByRole('button', { name: 'Publicar' });
        // Os macros e o alerta vêm da API; o alerta não impede publicar (BR-11).
        expect(screen.getByText(/Total do dia: 192,0 kcal · P 3,8 g/)).toBeInTheDocument();
        expect(screen.getByText(/Total da refeição: 192,0 kcal/)).toBeInTheDocument();
        expect(
            screen.getByText(/Diferença para o principal: -110,4 kcal · P -1,1 g · C -26,3 g · G \+0,7 g/),
        ).toBeInTheDocument();
        expect(screen.getByRole('note')).toHaveTextContent('Alergia ao trigo');
        await user.click(publishButton);
        expect(sent(`POST /meal-plans/${PLAN}/publish`)).toHaveLength(0);
        await user.click(screen.getByRole('button', { name: 'Confirmar publicação' }));
        await waitFor(() => expect(sent(`POST /meal-plans/${PLAN}/publish`)).toHaveLength(1));
        expect(await screen.findByText('Plano publicado: o paciente já pode vê-lo.')).toBeInTheDocument();
    });

    it('valida como a API antes de enviar e mostra no item o erro que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set(`GET /meal-plans/${PLAN}`, { status: 200, body: plan() });
        replies.set(`PUT /meal-plans/${PLAN}`, {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [
                    {
                        field: 'meals[0].items[0].foodId',
                        message:
                            'Alimento indisponível: ele não existe, foi arquivado ou é de outra nutricionista.',
                    },
                ],
            },
        });
        renderAt(`/app/clientes/${CLIENT}/planos/${PLAN}`);

        const amount = await screen.findByLabelText('Quantidade de Arroz, tipo 1, cozido');
        await user.clear(screen.getByLabelText('Nome do plano'));
        await user.type(screen.getByLabelText('Nome do plano'), 'P');
        await user.clear(amount);
        await user.type(amount, '0');
        await user.clear(screen.getByLabelText('Horário da refeição 1'));
        await user.type(screen.getByLabelText('Horário da refeição 1'), '7h');
        await user.click(screen.getByRole('button', { name: 'Salvar plano' }));

        expect(screen.getByText('O nome do plano precisa ter de 2 a 80 caracteres.')).toBeInTheDocument();
        expect(screen.getByText('A quantidade vai de 0,1 a 5000 g (ou ml).')).toBeInTheDocument();
        expect(screen.getByText('Informe o horário no formato HH:MM.')).toBeInTheDocument();
        expect(amount).toHaveAttribute('aria-invalid', 'true');
        expect(sent(`PUT /meal-plans/${PLAN}`)).toHaveLength(0);

        await user.type(screen.getByLabelText('Nome do plano'), 'lano');
        await user.clear(amount);
        await user.type(amount, '100');
        await user.clear(screen.getByLabelText('Horário da refeição 1'));
        await user.click(screen.getByRole('button', { name: 'Salvar plano' }));

        expect(
            await screen.findByText(
                'Alimento indisponível: ele não existe, foi arquivado ou é de outra nutricionista.',
            ),
        ).toBeInTheDocument();
        expect(sent(`PUT /meal-plans/${PLAN}`)[0]?.body).not.toHaveProperty('baseVersion');
    });

    it('não publica com refeição sem alimento', async () => {
        const user = userEvent.setup();
        replies.set(`GET /meal-plans/${PLAN}`, {
            status: 200,
            body: plan({
                meals: [
                    { position: 0, name: 'Almoço', items: [savedItem()], total: amounts(128, 2.5) },
                    { position: 1, name: 'Jantar', items: [], total: amounts(0, 0) },
                ],
            }),
        });
        renderAt(`/app/clientes/${CLIENT}/planos/${PLAN}`);

        await user.click(await screen.findByRole('button', { name: 'Publicar' }));

        expect(screen.getByText('Cada refeição precisa de ao menos um alimento.')).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Confirmar publicação' })).not.toBeInTheDocument();
    });

    it('editar o publicado avisa da nova versão, manda a versão base e recarrega num conflito', async () => {
        const user = userEvent.setup();
        replies.set(`GET /meal-plans/${PLAN}`, { status: 200, body: plan({ status: 'PUBLISHED' }) });
        replies.set(`PUT /meal-plans/${PLAN}`, { status: 409, body: { detail: 'O plano mudou.' } });
        renderAt(`/app/clientes/${CLIENT}/planos/${PLAN}`);

        expect(await screen.findByText(/salvar cria a versão 2/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Publicar' })).not.toBeInTheDocument();
        await user.clear(screen.getByLabelText('Nome do plano'));
        await user.type(screen.getByLabelText('Nome do plano'), 'Plano revisado');
        expect(screen.getByText('Salve para recalcular os macros e os alertas.')).toBeInTheDocument();

        replies.set(`GET /meal-plans/${PLAN}`, {
            status: 200,
            body: plan({
                status: 'PUBLISHED',
                version: 2,
                name: 'Plano da colega',
                updatedAt: '2026-11-25T13:00:00Z',
            }),
        });
        await user.click(screen.getByRole('button', { name: 'Salvar plano' }));

        expect(sent(`PUT /meal-plans/${PLAN}`)[0]?.body).toMatchObject({
            baseVersion: 1,
            name: 'Plano revisado',
        });
        expect(await screen.findByDisplayValue('Plano da colega')).toBeInTheDocument();
        expect(screen.getByText(/salvar cria a versão 3/)).toBeInTheDocument();
        expect(
            screen.getByText(
                'O plano mudou desde que você o abriu. Ele foi recarregado com o que está salvo.',
            ),
        ).toBeInTheDocument();
    });

    it('usa a medida caseira, ordena os itens e remove a alternativa', async () => {
        const user = userEvent.setup();
        replies.set(`GET /meal-plans/${PLAN}`, {
            status: 200,
            body: plan({
                meals: [
                    {
                        position: 0,
                        name: 'Almoço',
                        items: [
                            savedItem({
                                alternatives: [
                                    {
                                        foodId: PASTA,
                                        foodName: 'Macarrão, trigo, cozido',
                                        baseUnit: 'G',
                                        amount: 80,
                                        difference: amounts(-46.4, 0.2),
                                        alerts: [],
                                    },
                                ],
                            }),
                            savedItem({ foodId: BEANS, foodName: 'Feijão, carioca, cozido', amount: 50 }),
                        ],
                        total: amounts(166, 4.9),
                    },
                ],
            }),
        });
        replies.set(`GET /foods/${BEANS}`, {
            status: 200,
            body: {
                id: BEANS,
                name: 'Feijão, carioca, cozido',
                measures: [{ id: 'm-ladle', name: 'Concha', amount: 25 }],
            },
        });
        replies.set(`PUT /meal-plans/${PLAN}`, { status: 200, body: plan() });
        replies.set(`GET /clients/${CLIENT}/meal-plans`, { status: 200, body: [] });
        renderAt(`/app/clientes/${CLIENT}/planos/${PLAN}`);

        const rice = await screen.findByRole('article', { name: 'Arroz, tipo 1, cozido' });
        await user.click(
            within(rice).getByRole('button', { name: 'Remover a alternativa Macarrão, trigo, cozido' }),
        );
        await user.click(within(rice).getByRole('button', { name: 'Descer' }));
        const beans = screen.getByRole('article', { name: 'Feijão, carioca, cozido' });
        await user.click(within(beans).getByRole('button', { name: 'Usar medida caseira' }));
        await user.selectOptions(
            await within(beans).findByLabelText('Medida caseira de Feijão, carioca, cozido'),
            'm-ladle',
        );
        await user.clear(within(beans).getByLabelText('Quantas medidas de Feijão, carioca, cozido'));
        await user.type(within(beans).getByLabelText('Quantas medidas de Feijão, carioca, cozido'), '2');
        await user.click(screen.getByRole('button', { name: 'Salvar plano' }));

        await waitFor(() => expect(sent(`PUT /meal-plans/${PLAN}`)).toHaveLength(1));
        const body = sent(`PUT /meal-plans/${PLAN}`)[0]?.body as {
            meals: {
                items: {
                    foodId: string;
                    measureId?: string;
                    measureCount?: number;
                    alternatives: unknown[];
                }[];
            }[];
        };
        expect(body.meals[0]?.items.map((it) => it.foodId)).toEqual([BEANS, RICE]);
        expect(body.meals[0]?.items[0]).toMatchObject({ measureId: 'm-ladle', measureCount: 2 });
        expect(body.meals[0]?.items[0]).not.toHaveProperty('amount');
        expect(body.meals[0]?.items[1]?.alternatives).toEqual([]);
    });

    it('vínculo encerrado mostra que o acesso acabou', async () => {
        replies.set(`GET /meal-plans/${PLAN}`, {
            status: 404,
            body: { detail: 'Plano alimentar não encontrado.' },
        });
        renderAt(`/app/clientes/${CLIENT}/planos/${PLAN}`);

        expect(await screen.findByRole('alert')).toHaveTextContent('O acesso a este plano acabou');
    });
});

describe('validação do plano', () => {
    const form = (extra: Partial<MealPlanForm> = {}): MealPlanForm => ({
        name: 'Plano',
        startsOn: '2026-12-01',
        note: '',
        meals: [
            {
                name: 'Café',
                suggestedTime: '',
                note: '',
                items: [emptyItem({ id: RICE, name: 'Arroz', baseUnit: 'G' })],
            },
        ],
        ...extra,
    });

    it('espelha os limites da API', () => {
        expect(validate(form())).toEqual({});
        expect(validate(form({ meals: [] }))).toHaveProperty('meals');
        expect(validate(form({ startsOn: '' }))).toHaveProperty('startsOn');
        expect(validate(form({ note: 'o'.repeat(501) }))).toHaveProperty('note');
        const rice = emptyItem({ id: RICE, name: 'Arroz', baseUnit: 'G' });
        const meal = (extra: Record<string, unknown>) => ({
            name: 'Café',
            suggestedTime: '',
            note: '',
            items: [rice],
            ...extra,
        });
        expect(validate(form({ meals: [meal({ name: 'C' })] }))).toHaveProperty(['meals[0].name']);
        expect(validate(form({ meals: [meal({ note: 'o'.repeat(501) })] }))).toHaveProperty([
            'meals[0].note',
        ]);
        expect(
            validate(form({ meals: [meal({ items: Array.from({ length: 31 }, () => rice) })] })),
        ).toHaveProperty(['meals[0].items']);
        expect(validate(form({ meals: [meal({ items: Array.from({ length: 30 }, () => rice) })] }))).toEqual(
            {},
        );
        const eleven = Array.from({ length: 11 }, () => ({
            name: 'Lanche',
            suggestedTime: '',
            note: '',
            items: [],
        }));
        expect(validate(form({ meals: eleven }))).toHaveProperty('meals');
        const item = emptyItem({ id: RICE, name: 'Arroz', baseUnit: 'G' });
        const three = {
            ...item,
            alternatives: [
                { food: { id: 'a', name: 'A', baseUnit: 'G' }, amount: '10' },
                { food: { id: 'b', name: 'B', baseUnit: 'G' }, amount: '10' },
                { food: { id: 'c', name: 'C', baseUnit: 'G' }, amount: '5000.1' },
            ],
        };
        const errors = validate(
            form({ meals: [{ name: 'Café', suggestedTime: '24:00', note: '', items: [three] }] }),
        );
        expect(errors).toHaveProperty(['meals[0].items[0].alternatives']);
        expect(errors).toHaveProperty(['meals[0].items[0].alternatives[2].amount']);
        expect(errors).toHaveProperty(['meals[0].suggestedTime']);
        const measured = { ...item, measure: { id: 'm', name: 'Concha' }, measureCount: '101' };
        expect(
            validate(form({ meals: [{ name: 'Café', suggestedTime: '', note: '', items: [measured] }] })),
        ).toHaveProperty(['meals[0].items[0].measureCount']);
    });

    it('manda a medida e quantas no lugar da quantidade, e a versão base só quando informada', () => {
        const item = {
            ...emptyItem({ id: RICE, name: 'Arroz', baseUnit: 'G' }),
            measure: { id: 'm', name: 'Concha' },
            measureCount: '1,5',
        };
        const input = toInput(
            form({ meals: [{ name: 'Café', suggestedTime: '', note: '', items: [item] }] }),
            3,
        );
        expect(input.baseVersion).toBe(3);
        expect(input.meals[0]?.items?.[0]).toEqual({
            foodId: RICE,
            measureId: 'm',
            measureCount: 1.5,
            alternatives: [],
        });
    });
});
