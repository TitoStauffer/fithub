import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { PhotoComparison } from './PhotoComparison';

const API = 'http://localhost:8082';
const CLIENT = '0193a1b2-0000-7000-8000-00000000000c';
const PATH = `/professionals/me/clients/${CLIENT}/assessments`;

const HISTORY = [
    { id: 'a2', type: 'ONLINE', measuredOn: '2026-11-20', weightKg: 63 },
    { id: 'a1', type: 'PRESENTIAL', measuredOn: '2026-10-01', weightKg: 66 },
];

let routes: Map<string, () => { status: number; body?: unknown }>;
let calls: string[];

function photo(pose: string, url: string) {
    return { pose, url, expiresAt: '2026-11-23T12:05:00Z', uploadedAt: '2026-11-20T10:00:00Z' };
}

beforeEach(() => {
    calls = [];
    routes = new Map();
    routes.set(`GET ${PATH}`, () => ({ status: 200, body: HISTORY }));
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            const key = `${init?.method ?? 'GET'} ${url.replace(API, '')}`;
            calls.push(key);
            const route = routes.get(key);
            if (!route) return Promise.reject(new Error(`rota inesperada: ${key}`));
            const reply = route();
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

function renderComparison() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider client={fakeAuthClient({ user: userWith('personal') })}>
                <MemoryRouter>
                    <PhotoComparison clientId={CLIENT} />
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

async function chooseBoth(user: ReturnType<typeof userEvent.setup>) {
    await user.selectOptions(await screen.findByLabelText('Primeira data'), 'a1');
    await user.selectOptions(screen.getByLabelText('Segunda data'), 'a2');
}

describe('comparativo de fotos', () => {
    it('mostra as fotos da pose escolhida lado a lado, com as datas', async () => {
        const user = userEvent.setup();
        routes.set(`GET ${PATH}/a1/photos`, () => ({
            status: 200,
            body: [
                photo('FRONT', 'https://fotos.test/a1-front'),
                photo('SIDE', 'https://fotos.test/a1-side'),
            ],
        }));
        routes.set(`GET ${PATH}/a2/photos`, () => ({
            status: 200,
            body: [photo('FRONT', 'https://fotos.test/a2-front')],
        }));
        renderComparison();

        await chooseBoth(user);

        expect(await screen.findByAltText('Foto de frente em 01/10/2026')).toHaveAttribute(
            'src',
            'https://fotos.test/a1-front',
        );
        expect(await screen.findByAltText('Foto de frente em 20/11/2026')).toHaveAttribute(
            'src',
            'https://fotos.test/a2-front',
        );

        await user.selectOptions(screen.getByLabelText('Pose'), 'SIDE');
        expect(await screen.findByAltText('Foto de lado em 01/10/2026')).toBeInTheDocument();
        expect(await screen.findByText('Sem foto de lado nesta avaliação.')).toBeInTheDocument();
    });

    it('URL vencida: a tela pede uma nova e mostra a foto de novo', async () => {
        const user = userEvent.setup();
        let issued = 0;
        routes.set(`GET ${PATH}/a1/photos`, () => {
            issued++;
            return { status: 200, body: [photo('FRONT', `https://fotos.test/a1-front-${issued}`)] };
        });
        routes.set(`GET ${PATH}/a2/photos`, () => ({ status: 200, body: [] }));
        renderComparison();
        await chooseBoth(user);

        const first = await screen.findByAltText('Foto de frente em 01/10/2026');
        expect(first).toHaveAttribute('src', 'https://fotos.test/a1-front-1');
        fireEvent.error(first);

        await waitFor(() =>
            expect(screen.getByAltText('Foto de frente em 01/10/2026')).toHaveAttribute(
                'src',
                'https://fotos.test/a1-front-2',
            ),
        );

        // A URL nova também falhou: avisa em vez de pedir de novo sem parar.
        fireEvent.error(screen.getByAltText('Foto de frente em 01/10/2026'));
        expect(
            await screen.findByText('Não foi possível carregar a foto. Tente de novo mais tarde.'),
        ).toBeInTheDocument();
        expect(calls.filter((call) => call === `GET ${PATH}/a1/photos`)).toHaveLength(2);
    });

    it('sem vínculo ativo, a API responde 404 e a tela mostra que o acesso acabou', async () => {
        const user = userEvent.setup();
        routes.set(`GET ${PATH}/a1/photos`, () => ({
            status: 404,
            body: { detail: 'Cliente não encontrado.' },
        }));
        routes.set(`GET ${PATH}/a2/photos`, () => ({
            status: 404,
            body: { detail: 'Cliente não encontrado.' },
        }));
        renderComparison();
        await chooseBoth(user);

        const alerts = await screen.findAllByRole('alert');
        expect(alerts[0]).toHaveTextContent('O acesso a este cliente acabou');
        expect(screen.queryByRole('img')).toBeNull();
    });
});
