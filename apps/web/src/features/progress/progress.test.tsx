import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { ClientProgressSection } from './ClientProgressSection';
import { chartCoordinates } from './chartGeometry';
import { periodStart } from './progressPeriod';

const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const NOW = new Date(2026, 10, 25, 12);
const PROGRESS = `http://localhost:8083/clients/${CLIENT}/progress/training`;
const RECORDS = `http://localhost:8083/clients/${CLIENT}/records`;
const SERIES = `http://localhost:8082/professionals/me/clients/${CLIENT}/assessments/series`;

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

function renderSection() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith('personal') })}>
                <ClientProgressSection clientId={CLIENT} now={NOW} />
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const PROGRESS_BODY = {
    from: '2026-10-29',
    to: '2026-11-25',
    adherence: {
        done: 5,
        expected: 6.5,
        percent: 76.9,
        weeks: [
            { start: '2026-10-29', end: '2026-11-04', done: 2, expected: 3 },
            { start: '2026-11-05', end: '2026-11-11', done: 3, expected: 3.5 },
        ],
    },
    exercises: [
        {
            exerciseId: 'e1',
            name: 'Supino reto',
            points: [
                { date: '2026-11-03', maxLoadKg: 40, volumeKg: 1200 },
                { date: '2026-11-10', maxLoadKg: 42.5, volumeKg: 1275 },
            ],
        },
        {
            exerciseId: 'e2',
            name: 'Agachamento',
            points: [{ date: '2026-11-04', maxLoadKg: 60, volumeKg: 1800 }],
        },
        { exerciseId: 'e3', name: 'Prancha', points: [] },
    ],
};

const SERIES_BODY = {
    weightKg: [
        { date: '2026-10-01', value: 72.5 },
        { date: '2026-11-01', value: 71 },
    ],
    circumferences: { waist: [{ date: '2026-11-01', value: 70.5 }] },
    bodyFat: [
        { origin: 'NAVY', points: [{ date: '2026-10-01', value: 25 }] },
        { origin: 'BIOIMPEDANCE', points: [{ date: '2026-11-01', value: 24.8 }] },
    ],
};

function withData() {
    replies.set(`${PROGRESS}?from=2026-10-29`, { status: 200, body: PROGRESS_BODY });
    replies.set(`${PROGRESS}?from=2026-10-01`, {
        status: 200,
        body: { ...PROGRESS_BODY, from: '2026-10-01' },
    });
    replies.set(RECORDS, {
        status: 200,
        body: [
            {
                exerciseId: 'e1',
                exerciseName: 'Supino reto',
                maxLoad: { date: '2026-11-10', loadKg: 42.5, reps: 8, valueKg: 42.5, source: 'SET' },
                estimatedOneRepMax: {
                    date: '2026-11-10',
                    loadKg: 42.5,
                    reps: 8,
                    valueKg: 53.8,
                    source: 'SET',
                },
            },
            {
                // Lançado à mão pelo aluno (DEV-0158).
                exerciseId: 'e3',
                exerciseName: 'Remada curvada',
                maxLoad: { date: '2026-11-12', loadKg: 50, reps: 5, valueKg: 50, source: 'MANUAL' },
                estimatedOneRepMax: {
                    date: '2026-11-12',
                    loadKg: 50,
                    reps: 5,
                    valueKg: 58.3,
                    source: 'MANUAL',
                },
            },
        ],
    });
    replies.set(SERIES, { status: 200, body: SERIES_BODY });
}

describe('evolução do aluno (DEV-0094)', () => {
    it('mostra a aderência, a carga do exercício, os recordes e a evolução corporal', async () => {
        withData();
        renderSection();

        expect(await screen.findByText('5 de 6,5 treinos esperados (76,9%)')).toBeInTheDocument();
        const weekly = screen.getByRole('table', { name: 'Treinos finalizados por semana' });
        expect(within(weekly).getByRole('row', { name: '29/10/2026 a 04/11/2026 2' })).toBeInTheDocument();

        // O exercício sem ponto no período não aparece na escolha.
        const exercise = screen.getByLabelText('Exercício');
        expect(
            within(exercise)
                .getAllByRole('option')
                .map((option) => option.textContent),
        ).toEqual(['Supino reto', 'Agachamento']);
        const load = screen.getByRole('table', { name: 'Maior carga: Supino reto' });
        expect(within(load).getByRole('row', { name: '10/11/2026 42,5' })).toBeInTheDocument();
        expect(screen.getByRole('table', { name: 'Volume: Supino reto' })).toHaveTextContent('1.275');
        await userEvent.selectOptions(exercise, 'e2');
        expect(screen.getByRole('table', { name: 'Maior carga: Agachamento' })).toHaveTextContent('60');

        const records = await screen.findByRole('table', { name: 'Recordes por exercício' });
        expect(within(records).getByRole('row', { name: /Supino reto/ })).toHaveTextContent(
            'Supino reto42,5 kg × 8 em 10/11/202653,8 kg',
        );
        // O recorde manual vem marcado nas duas colunas; o do treino, não (FR-54, DEV-0158).
        expect(within(records).getByRole('row', { name: /Remada curvada/ })).toHaveTextContent(
            'Remada curvada50 kg × 5 em 12/11/2026 (manual)58,3 kg (manual)',
        );

        // O % de gordura vem por método, sem misturar (BR-12).
        const metric = await screen.findByLabelText('Medida');
        expect(
            within(metric)
                .getAllByRole('option')
                .map((option) => option.textContent),
        ).toEqual(['Peso', 'Cintura', '% de gordura (Marinha (online))', '% de gordura (Bioimpedância)']);
        expect(screen.getByRole('table', { name: 'Peso' })).toHaveTextContent('01/10/202672,5');
        await userEvent.selectOptions(metric, 'fat:BIOIMPEDANCE');
        const fat = screen.getByRole('table', { name: '% de gordura (Bioimpedância)' });
        expect(within(fat).getAllByRole('row')).toHaveLength(2);
        expect(fat).toHaveTextContent('24,8');
    });

    it('troca o período pedindo a aderência do novo início', async () => {
        withData();
        renderSection();
        await screen.findByText('5 de 6,5 treinos esperados (76,9%)');

        await userEvent.selectOptions(screen.getByLabelText('Período'), '8');
        await screen.findByText('5 de 6,5 treinos esperados (76,9%)');
        expect(requested).toContain(`${PROGRESS}?from=2026-10-01`);
    });

    it('sem dados, cada bloco diz o que falta', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, {
            status: 200,
            body: { adherence: { done: 0, expected: 0, weeks: [] }, exercises: [] },
        });
        replies.set(RECORDS, { status: 200, body: [] });
        replies.set(SERIES, { status: 200, body: { weightKg: [], circumferences: {}, bodyFat: [] } });
        renderSection();

        expect(await screen.findByText('Nenhuma ficha publicada no período.')).toBeInTheDocument();
        expect(screen.getByText('Nenhum treino finalizado no período.')).toBeInTheDocument();
        expect(await screen.findByText('Nenhum recorde ainda.')).toBeInTheDocument();
        expect(await screen.findByText('Nenhuma avaliação ainda.')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('sem vínculo, avisa que o acesso acabou', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, { status: 404, body: { detail: 'Sem vínculo.' } });
        replies.set(RECORDS, { status: 404, body: { detail: 'Sem vínculo.' } });
        replies.set(SERIES, { status: 404, body: { detail: 'Sem vínculo.' } });
        renderSection();

        expect(
            await screen.findAllByText('O acesso a este cliente acabou: o vínculo foi encerrado.'),
        ).toHaveLength(3);
    });

    it('o período começa no dia certo e o gráfico põe o maior valor em cima', () => {
        expect(periodStart(NOW, 4)).toBe('2026-10-29');
        expect(periodStart(NOW, 12)).toBe('2026-09-03');
        const [low, high] = chartCoordinates([
            { label: 'a', value: 10 },
            { label: 'b', value: 20 },
        ]);
        expect(high!.y).toBeLessThan(low!.y);
        expect(high!.x).toBeGreaterThan(low!.x);
        expect(chartCoordinates([{ label: 'a', value: 5 }])).toEqual([{ x: 160, y: 60 }]);
    });
});
