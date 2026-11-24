import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';
import type { Food, FoodSummary } from '@/lib/nutritionApi';

import { recipePreview, validate, EMPTY_FORM } from './foodForm';
import { FoodFormPage } from './FoodFormPage';
import { FoodsPage } from './FoodsPage';

const API = 'http://localhost:8084';
const FIRST_PAGE = '/foods?page=0&size=20';

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const beans: FoodSummary = {
    id: 'f-beans',
    kind: 'TACO',
    name: 'Feijão, preto, cozido',
    baseUnit: 'G',
    energyKcal: 77,
    proteinG: 4.5,
    carbsG: 14,
    fatG: 0.5,
    vegan: true,
    recipe: false,
};

const granola: FoodSummary = {
    id: 'f-granola',
    kind: 'OWN',
    name: 'Granola da casa',
    baseUnit: 'G',
    energyKcal: 400,
    proteinG: 10,
    carbsG: 60,
    fatG: 15,
    vegan: false,
    recipe: false,
};

const rice: FoodSummary = {
    id: 'f-rice',
    kind: 'TACO',
    name: 'Arroz, tipo 1, cozido',
    baseUnit: 'G',
    energyKcal: 128,
    proteinG: 2.5,
    carbsG: 28.1,
    fatG: 0.2,
    vegan: true,
    recipe: false,
};

const page = (items: FoodSummary[]) => ({ items, total: items.length, page: 0, size: 20 });

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
                        <Route path="/app/alimentos" element={<FoodsPage />} />
                        <Route path="/app/alimentos/novo" element={<FoodFormPage />} />
                        <Route path="/app/alimentos/:foodId/editar" element={<FoodFormPage />} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('catálogo de alimentos', () => {
    it('busca "feijao" com o filtro vegano e mostra os valores e a origem', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([beans, granola]) });
        replies.set('GET /foods?name=feijao&vegan=true&page=0&size=20', { status: 200, body: page([beans]) });
        renderAt('/app/alimentos');

        const own = await screen.findByRole('article', { name: 'Granola da casa' });
        expect(within(own).getByText('Próprio')).toBeInTheDocument();
        expect(within(own).getByRole('link', { name: 'Editar' })).toHaveAttribute(
            'href',
            '/app/alimentos/f-granola/editar',
        );

        await user.type(screen.getByLabelText('Nome'), 'feijao');
        await user.selectOptions(screen.getByLabelText('Vegano'), 'true');
        await user.click(screen.getByRole('button', { name: 'Buscar' }));

        const card = await screen.findByRole('article', { name: 'Feijão, preto, cozido' });
        expect(within(card).getByText('TACO')).toBeInTheDocument();
        expect(within(card).getByText('Vegano')).toBeInTheDocument();
        expect(card).toHaveTextContent('Por 100 g: 77 kcal · P 4,5 g · C 14 g · G 0,5 g');
        // A TACO não é da nutricionista: sem editar nem arquivar.
        expect(within(card).queryByRole('button', { name: 'Arquivar' })).not.toBeInTheDocument();
        expect(screen.queryByRole('article', { name: 'Granola da casa' })).not.toBeInTheDocument();
    });

    it('arquivar pede confirmação e o alimento sai da busca', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([beans, granola]) });
        replies.set('POST /foods/f-granola/archive', { status: 200, body: { ...granola, archived: true } });
        renderAt('/app/alimentos');

        const own = await screen.findByRole('article', { name: 'Granola da casa' });
        await user.click(within(own).getByRole('button', { name: 'Arquivar' }));
        expect(sent('POST /foods/f-granola/archive')).toHaveLength(0);
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([beans]) });
        await user.click(within(own).getByRole('button', { name: 'Confirmar arquivamento' }));

        await waitFor(() => expect(sent('POST /foods/f-granola/archive')).toHaveLength(1));
        await waitFor(() =>
            expect(screen.queryByRole('article', { name: 'Granola da casa' })).not.toBeInTheDocument(),
        );
    });
});

describe('cadastro de alimento e receita', () => {
    it('cria o alimento próprio; o erro da API aparece no campo', async () => {
        const user = userEvent.setup();
        replies.set('POST /foods', {
            status: 400,
            body: {
                title: 'Dados inválidos',
                status: 400,
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'name', message: 'O nome precisa ter de 2 a 120 caracteres.' }],
            },
        });
        renderAt('/app/alimentos/novo');

        await user.type(screen.getByLabelText('Nome'), 'Granola da casa');
        await user.type(screen.getByLabelText('Energia (kcal)'), '400');
        await user.type(screen.getByLabelText('Proteína (g)'), '10');
        await user.type(screen.getByLabelText('Carboidrato (g)'), '60');
        await user.type(screen.getByLabelText('Gordura (g)'), '15,5');
        await user.click(screen.getByRole('button', { name: 'Salvar' }));

        expect(await screen.findByText('O nome precisa ter de 2 a 120 caracteres.')).toBeInTheDocument();
        expect(sent('POST /foods')[0]?.body).toEqual({
            name: 'Granola da casa',
            baseUnit: 'G',
            energyKcal: 400,
            proteinG: 10,
            carbsG: 60,
            fatG: 15.5,
            vegan: false,
        });
        expect(screen.getByLabelText('Nome')).toHaveValue('Granola da casa');
    });

    it('valida como a API antes de enviar', async () => {
        const user = userEvent.setup();
        renderAt('/app/alimentos/novo');

        await user.type(screen.getByLabelText('Nome'), 'Pasta');
        await user.type(screen.getByLabelText('Energia (kcal)'), '500');
        await user.type(screen.getByLabelText('Proteína (g)'), '50');
        await user.type(screen.getByLabelText('Carboidrato (g)'), '40');
        await user.type(screen.getByLabelText('Gordura (g)'), '20');
        await user.click(screen.getByRole('button', { name: 'Salvar' }));

        expect(
            screen.getByText('Proteína, carboidrato e gordura somam no máximo 100 g por 100 g.'),
        ).toBeInTheDocument();
        expect(sent('POST /foods')).toHaveLength(0);
    });

    it('a receita mostra os valores calculados antes de salvar e envia ingredientes e rendimento', async () => {
        const user = userEvent.setup();
        replies.set('GET /foods?name=arroz&size=10', { status: 200, body: page([rice]) });
        replies.set('GET /foods?name=feijao&size=10', { status: 200, body: page([beans]) });
        replies.set('POST /foods', {
            status: 201,
            body: { id: 'f-new', kind: 'OWN', name: 'Arroz com feijão' },
        });
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([]) });
        renderAt('/app/alimentos/novo');

        await user.type(screen.getByLabelText('Nome'), 'Arroz com feijão');
        await user.click(screen.getByRole('checkbox', { name: /É uma receita/ }));
        for (const [query, food, amount] of [
            ['arroz', 'Arroz, tipo 1, cozido', '200'],
            ['feijao', 'Feijão, preto, cozido', '100'],
        ] as const) {
            await user.type(screen.getByLabelText('Buscar ingrediente'), query);
            await user.click(screen.getByRole('button', { name: 'Buscar ingrediente' }));
            await user.click(await screen.findByRole('button', { name: `Adicionar ${food}` }));
            await user.type(screen.getByLabelText(`Quantidade (g ou ml) de ${food}`), amount);
        }
        await user.type(screen.getByLabelText('Rendimento total (g)'), '300');

        // (2,5 × 200 + 4,5 × 100) / 300 = 3,17 g de proteína por 100 g.
        expect(screen.getByRole('status')).toHaveTextContent('Por 100 g da receita: 111 kcal · P 3,17 g');
        expect(screen.getByRole('status')).toHaveTextContent('vegana');
        await user.click(screen.getByRole('button', { name: 'Salvar' }));

        await waitFor(() => expect(sent('POST /foods')).toHaveLength(1));
        expect(sent('POST /foods')[0]?.body).toEqual({
            name: 'Arroz com feijão',
            baseUnit: 'G',
            recipe: {
                ingredients: [
                    { foodId: 'f-rice', amount: 200 },
                    { foodId: 'f-beans', amount: 100 },
                ],
                yieldGrams: 300,
            },
        });
    });

    it('edita o próprio com as medidas caseiras dele', async () => {
        const user = userEvent.setup();
        const food: Food = {
            id: 'f-granola',
            kind: 'OWN',
            name: 'Granola da casa',
            baseUnit: 'G',
            energyKcal: 400,
            proteinG: 10,
            carbsG: 60,
            fatG: 15,
            vegan: false,
            measures: [{ id: 'm-1', name: 'Colher de sopa', amount: 15 }],
            archived: false,
        };
        replies.set('GET /foods/f-granola', { status: 200, body: food });
        replies.set('POST /foods/f-granola/measures', {
            status: 201,
            body: { id: 'm-2', name: 'Xícara', amount: 80 },
        });
        renderAt('/app/alimentos/f-granola/editar');

        expect(await screen.findByLabelText('Nome')).toHaveValue('Granola da casa');
        expect(screen.getByText('Colher de sopa: 15 g')).toBeInTheDocument();
        await user.type(screen.getByLabelText('Nome da medida'), 'Xícara');
        await user.type(screen.getByLabelText('Vale (g)'), '80');
        await user.click(screen.getByRole('button', { name: 'Adicionar medida' }));

        await waitFor(() => expect(sent('POST /foods/f-granola/measures')).toHaveLength(1));
        expect(sent('POST /foods/f-granola/measures')[0]?.body).toEqual({ name: 'Xícara', amount: 80 });
    });
});

describe('formulário', () => {
    it('receita sem rendimento não tem prévia e cobra os campos', () => {
        const form = { ...EMPTY_FORM, name: 'Receita', isRecipe: true };
        expect(recipePreview(form)).toBeUndefined();
        expect(validate(form)).toEqual({
            'recipe.ingredients': 'A receita tem de 1 a 30 ingredientes.',
            'recipe.yieldGrams': 'O rendimento vai de 1 a 20000 g.',
        });
    });
});
