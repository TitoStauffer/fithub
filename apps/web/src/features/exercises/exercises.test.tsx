import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';
import type { Exercise } from '@/lib/trainingApi';

import { ExerciseFormPage } from './ExerciseFormPage';
import { ExercisesPage } from './ExercisesPage';

const API = 'http://localhost:8083';
const FIRST_PAGE = '/exercises?page=0&size=20';

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let requests: { key: string; body?: unknown }[];

const bench: Exercise = {
    id: 'e-1',
    kind: 'CATALOG',
    name: 'Supino reto',
    muscleGroups: ['CHEST', 'TRICEPS'],
    equipment: 'BARBELL',
    instructions: 'Deite no banco e desça a barra até o peito.',
    mediaUrl: 'https://wger.de/media/supino.png',
    attribution: {
        source: 'wger',
        license: 'CC-BY-SA 3',
        author: 'wger.de',
        mediaLicense: 'CC-BY-SA 4',
        mediaAuthor: 'Everkinetic',
    },
};

const curl: Exercise = {
    id: 'e-2',
    kind: 'OWN',
    name: 'Rosca do meu jeito',
    muscleGroups: ['BICEPS'],
    equipment: 'DUMBBELL',
    instructions: null,
    mediaUrl: null,
};

const page = (items: Exercise[], total = items.length) => ({ items, total, page: 0, size: 20 });

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
                        <Route path="/app/exercicios" element={<ExercisesPage />} />
                        <Route path="/app/exercicios/novo" element={<ExerciseFormPage />} />
                        <Route path="/app/exercicios/:exerciseId/editar" element={<ExerciseFormPage />} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const sent = (key: string) => requests.filter((request) => request.key === key);

describe('catálogo de exercícios', () => {
    it('mostra grupos, equipamento, instruções, o link da mídia e a atribuição do importado', async () => {
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([bench, curl]) });
        renderAt('/app/exercicios');

        const card = await screen.findByRole('article', { name: 'Supino reto' });
        expect(within(card).getByText('Catálogo')).toBeInTheDocument();
        expect(within(card).getByText('Peito, Tríceps · Barra')).toBeInTheDocument();
        expect(within(card).getByText('Deite no banco e desça a barra até o peito.')).toBeInTheDocument();
        const media = within(card).getByRole('link', { name: 'Ver vídeo ou imagem' });
        expect(media).toHaveAttribute('href', 'https://wger.de/media/supino.png');
        expect(media).toHaveAttribute('target', '_blank');
        expect(media).toHaveAttribute('rel', 'noopener noreferrer');
        expect(card).toHaveTextContent('Fonte: wger · Licença CC-BY-SA 3 · Autor: wger.de');
        expect(card).toHaveTextContent('Mídia: licença CC-BY-SA 4, autor Everkinetic');
        // O catálogo não é do personal: sem editar nem arquivar.
        expect(within(card).queryByRole('button', { name: 'Arquivar' })).not.toBeInTheDocument();

        const own = screen.getByRole('article', { name: 'Rosca do meu jeito' });
        expect(within(own).getByText('Próprio')).toBeInTheDocument();
        expect(within(own).queryByText(/Fonte:/)).not.toBeInTheDocument();
        expect(within(own).queryByRole('link', { name: 'Ver vídeo ou imagem' })).not.toBeInTheDocument();
        expect(within(own).getByRole('link', { name: 'Editar' })).toHaveAttribute(
            'href',
            '/app/exercicios/e-2/editar',
        );
        expect(screen.getByText('2 exercícios')).toBeInTheDocument();
    });

    it('busca por nome com os filtros de grupo e equipamento', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([bench, curl]) });
        replies.set('GET /exercises?name=supino&muscleGroup=CHEST&equipment=BARBELL&page=0&size=20', {
            status: 200,
            body: page([bench]),
        });
        renderAt('/app/exercicios');
        await screen.findByRole('article', { name: 'Rosca do meu jeito' });

        await user.type(screen.getByLabelText('Nome'), ' supino ');
        await user.selectOptions(screen.getByLabelText('Grupo muscular'), 'CHEST');
        await user.selectOptions(screen.getByLabelText('Equipamento'), 'BARBELL');
        await user.click(screen.getByRole('button', { name: 'Buscar' }));

        await waitFor(() =>
            expect(screen.queryByRole('article', { name: 'Rosca do meu jeito' })).not.toBeInTheDocument(),
        );
        expect(screen.getByRole('article', { name: 'Supino reto' })).toBeInTheDocument();
        expect(screen.getByText('1 exercício')).toBeInTheDocument();
    });

    it('pagina com o total e avisa quando não há resultado ou quando a busca falha', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([bench], 45) });
        replies.set('GET /exercises?page=1&size=20', { status: 200, body: { ...page([curl], 45), page: 1 } });
        replies.set('GET /exercises?name=nada&page=0&size=20', { status: 200, body: page([]) });
        replies.set('GET /exercises?name=falha&page=0&size=20', {
            status: 503,
            body: { detail: 'Serviço de treino indisponível.' },
        });
        renderAt('/app/exercicios');

        expect(screen.getByRole('status')).toHaveTextContent('Carregando exercícios…');
        expect(await screen.findByText('Página 1 de 3')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
        await user.click(screen.getByRole('button', { name: 'Próxima' }));
        expect(await screen.findByText('Página 2 de 3')).toBeInTheDocument();
        expect(await screen.findByRole('article', { name: 'Rosca do meu jeito' })).toBeInTheDocument();

        await user.type(screen.getByLabelText('Nome'), 'nada');
        await user.click(screen.getByRole('button', { name: 'Buscar' }));
        expect(await screen.findByText('Nenhum exercício encontrado com esses filtros.')).toBeInTheDocument();

        await user.clear(screen.getByLabelText('Nome'));
        await user.type(screen.getByLabelText('Nome'), 'falha');
        await user.click(screen.getByRole('button', { name: 'Buscar' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Serviço de treino indisponível.');
        expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
    });

    it('arquiva o exercício próprio só depois da confirmação', async () => {
        const user = userEvent.setup();
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([bench, curl]) });
        replies.set('DELETE /exercises/e-2', { status: 204 });
        renderAt('/app/exercicios');
        const own = await screen.findByRole('article', { name: 'Rosca do meu jeito' });

        await user.click(within(own).getByRole('button', { name: 'Arquivar' }));
        expect(within(own).getByText(/as fichas que já o usam continuam mostrando/)).toBeInTheDocument();
        await user.click(within(own).getByRole('button', { name: 'Cancelar' }));
        expect(sent('DELETE /exercises/e-2')).toHaveLength(0);

        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([bench]) });
        await user.click(within(own).getByRole('button', { name: 'Arquivar' }));
        await user.click(within(own).getByRole('button', { name: 'Confirmar arquivamento' }));

        await waitFor(() => expect(sent('DELETE /exercises/e-2')).toHaveLength(1));
        await waitFor(() =>
            expect(screen.queryByRole('article', { name: 'Rosca do meu jeito' })).not.toBeInTheDocument(),
        );
    });
});

describe('cadastro de exercício próprio', () => {
    it('valida como a API antes de enviar e cria com os campos', async () => {
        const user = userEvent.setup();
        replies.set('POST /exercises', { status: 201, body: curl });
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([curl]) });
        renderAt('/app/exercicios/novo');

        await user.click(screen.getByRole('button', { name: 'Salvar exercício' }));
        expect(screen.getByText('O nome precisa ter de 2 a 120 caracteres.')).toBeInTheDocument();
        expect(screen.getByText('Informe ao menos um grupo muscular.')).toBeInTheDocument();
        expect(screen.getByText('Informe o equipamento.')).toBeInTheDocument();
        expect(sent('POST /exercises')).toHaveLength(0);

        await user.type(screen.getByLabelText('Nome'), 'Rosca do meu jeito');
        await user.click(screen.getByLabelText('Bíceps'));
        await user.click(screen.getByLabelText('Antebraços'));
        await user.selectOptions(screen.getByLabelText('Equipamento'), 'DUMBBELL');
        await user.type(
            screen.getByLabelText('Link do vídeo ou da imagem (opcional)'),
            'http://videos.example/rosca',
        );
        await user.click(screen.getByRole('button', { name: 'Salvar exercício' }));
        expect(
            screen.getByText('O link da mídia precisa ser um endereço https de até 500 caracteres.'),
        ).toBeInTheDocument();
        expect(screen.getByLabelText('Link do vídeo ou da imagem (opcional)')).toHaveAttribute(
            'aria-invalid',
            'true',
        );
        expect(sent('POST /exercises')).toHaveLength(0);

        await user.clear(screen.getByLabelText('Link do vídeo ou da imagem (opcional)'));
        await user.type(
            screen.getByLabelText('Link do vídeo ou da imagem (opcional)'),
            'https://videos.example/rosca',
        );
        await user.type(screen.getByLabelText('Instruções (opcional)'), 'Cotovelos colados.');
        await user.click(screen.getByRole('button', { name: 'Salvar exercício' }));

        expect(await screen.findByRole('article', { name: 'Rosca do meu jeito' })).toBeInTheDocument();
        expect(sent('POST /exercises')[0]?.body).toEqual({
            name: 'Rosca do meu jeito',
            muscleGroups: ['BICEPS', 'FOREARMS'],
            equipment: 'DUMBBELL',
            instructions: 'Cotovelos colados.',
            mediaUrl: 'https://videos.example/rosca',
        });
    });

    it('mostra no campo o erro que a API devolver', async () => {
        const user = userEvent.setup();
        replies.set('POST /exercises', {
            status: 400,
            body: {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'name', message: 'O nome precisa ter de 2 a 120 caracteres.' }],
            },
        });
        renderAt('/app/exercicios/novo');

        await user.type(screen.getByLabelText('Nome'), 'Rosca');
        await user.click(screen.getByLabelText('Bíceps'));
        await user.selectOptions(screen.getByLabelText('Equipamento'), 'DUMBBELL');
        await user.click(screen.getByRole('button', { name: 'Salvar exercício' }));

        expect(await screen.findByText('O nome precisa ter de 2 a 120 caracteres.')).toBeInTheDocument();
        expect(screen.getByLabelText('Nome')).toHaveAttribute('aria-invalid', 'true');
    });

    it('edita o exercício próprio a partir do que já está salvo', async () => {
        const user = userEvent.setup();
        replies.set('GET /exercises/e-2', { status: 200, body: curl });
        replies.set('PUT /exercises/e-2', { status: 200, body: { ...curl, name: 'Rosca martelo' } });
        replies.set(`GET ${FIRST_PAGE}`, { status: 200, body: page([{ ...curl, name: 'Rosca martelo' }]) });
        renderAt('/app/exercicios/e-2/editar');

        const name = await screen.findByLabelText('Nome');
        expect(name).toHaveValue('Rosca do meu jeito');
        expect(screen.getByLabelText('Bíceps')).toBeChecked();
        expect(screen.getByLabelText('Equipamento')).toHaveValue('DUMBBELL');
        await user.clear(name);
        await user.type(name, 'Rosca martelo');
        await user.click(screen.getByRole('button', { name: 'Salvar exercício' }));

        expect(await screen.findByRole('article', { name: 'Rosca martelo' })).toBeInTheDocument();
        expect(sent('PUT /exercises/e-2')[0]?.body).toEqual({
            name: 'Rosca martelo',
            muscleGroups: ['BICEPS'],
            equipment: 'DUMBBELL',
            instructions: null,
            mediaUrl: null,
        });
    });
});
