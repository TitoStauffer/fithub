import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { PatientProgressSection } from './PatientProgressSection';

const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const NOW = new Date(2026, 10, 25, 12);
const PROGRESS = `http://localhost:8084/clients/${CLIENT}/progress/nutrition`;
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
            <AuthProvider client={fakeAuthClient({ user: userWith('nutritionist') })}>
                <PatientProgressSection clientId={CLIENT} now={NOW} />
            </AuthProvider>
        </QueryClientProvider>,
    );
}

const PROGRESS_BODY = {
    from: '2026-10-29',
    to: '2026-11-25',
    adherence: {
        done: 3,
        prescribed: 8,
        percent: 37.5,
        days: [
            { day: '2026-11-24', done: 1.5, prescribed: 4 },
            { day: '2026-11-25', done: 1.5, prescribed: 4 },
        ],
    },
    alternatives: [
        { foodId: 'f1', foodName: 'Feijão', times: 3 },
        { foodId: 'f2', foodName: 'Batata-doce', times: 1 },
    ],
    deviations: [
        {
            logId: 'l1',
            day: '2026-11-25',
            mealName: 'Almoço',
            deviation: 'Comi pizza',
            photoUrl: 'https://fotos.example/l1?assinatura',
        },
        { logId: 'l2', day: '2026-11-24', mealName: 'Jantar', deviation: 'Pulei a salada' },
    ],
};

function withSeries() {
    replies.set(SERIES, {
        status: 200,
        body: {
            weightKg: [{ date: '2026-11-01', value: 64.5 }],
            circumferences: {},
            bodyFat: [{ origin: 'BIOIMPEDANCE', points: [{ date: '2026-11-01', value: 28 }] }],
        },
    });
}

describe('evolução do paciente (DEV-0095)', () => {
    it('mostra a aderência, as alternativas, os desvios com a foto e a evolução corporal', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, { status: 200, body: PROGRESS_BODY });
        withSeries();
        renderSection();

        expect(await screen.findByText('3 de 8 refeições prescritas (37,5%)')).toBeInTheDocument();
        const daily = screen.getByRole('table', { name: 'Refeições feitas por dia' });
        expect(within(daily).getByRole('row', { name: '24/11/2026 1,5' })).toBeInTheDocument();

        expect(screen.getByText('Feijão: 3 vezes')).toBeInTheDocument();
        expect(screen.getByText('Batata-doce: 1 vez')).toBeInTheDocument();

        const deviations = within(screen.getByRole('list', { name: 'Desvios' })).getAllByRole('listitem');
        expect(deviations).toHaveLength(2);
        expect(deviations[0]).toHaveTextContent('25/11/2026 · Almoço');
        expect(deviations[0]).toHaveTextContent('Comi pizza');
        const photo = within(deviations[0]!).getByRole('link', { name: 'Ver foto do prato' });
        expect(photo).toHaveAttribute('href', 'https://fotos.example/l1?assinatura');
        expect(photo).toHaveAttribute('rel', 'noopener noreferrer');
        // Sem foto, nem o link aparece (um <a> sem href não tem papel de link; por isso conta o texto).
        expect(within(deviations[1]!).queryByText('Ver foto do prato')).not.toBeInTheDocument();

        const metric = await screen.findByLabelText('Medida');
        expect(
            within(metric)
                .getAllByRole('option')
                .map((option) => option.textContent),
        ).toEqual(['Peso', '% de gordura (Bioimpedância)']);
        expect(screen.getByRole('table', { name: 'Peso' })).toHaveTextContent('64,5');
    });

    it('troca o período pedindo o novo início', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, { status: 200, body: PROGRESS_BODY });
        replies.set(`${PROGRESS}?from=2026-09-03`, {
            status: 200,
            body: { ...PROGRESS_BODY, from: '2026-09-03' },
        });
        withSeries();
        renderSection();
        await screen.findByText('3 de 8 refeições prescritas (37,5%)');

        await userEvent.selectOptions(screen.getByLabelText('Período'), '12');
        await screen.findByText('3 de 8 refeições prescritas (37,5%)');
        expect(requested).toContain(`${PROGRESS}?from=2026-09-03`);
    });

    it('sem dados, cada bloco diz o que falta', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, {
            status: 200,
            body: { adherence: { done: 0, prescribed: 0, days: [] }, alternatives: [], deviations: [] },
        });
        replies.set(SERIES, { status: 200, body: { weightKg: [], circumferences: {}, bodyFat: [] } });
        renderSection();

        expect(await screen.findByText('Nenhum plano publicado no período.')).toBeInTheDocument();
        expect(screen.getByText('Nenhuma alternativa usada no período.')).toBeInTheDocument();
        expect(screen.getByText('Nenhum desvio registrado no período.')).toBeInTheDocument();
        expect(await screen.findByText('Nenhuma avaliação ainda.')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('sem vínculo, avisa que o acesso acabou', async () => {
        replies.set(`${PROGRESS}?from=2026-10-29`, { status: 404, body: { detail: 'Sem vínculo.' } });
        replies.set(SERIES, { status: 404, body: { detail: 'Sem vínculo.' } });
        renderSection();

        expect(
            await screen.findAllByText('O acesso a este cliente acabou: o vínculo foi encerrado.'),
        ).toHaveLength(2);
        expect(screen.queryByText('Nenhuma alternativa usada no período.')).not.toBeInTheDocument();
    });
});
