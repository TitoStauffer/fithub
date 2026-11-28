import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { fakeAuthClient, userWith } from '@/features/auth/fakeAuthClient';

import { daysLeft, offerText } from './subscription';
import { SubscriptionPage } from './SubscriptionPage';

const BASE = 'http://localhost:8081/professionals/me/subscription';
const NOW = new Date('2026-11-28T15:00:00Z');
const LAUNCH_OFFER = { value: 150, launchPrice: true, launchMonths: 3, regularValue: 200 };

function subscription(overrides: Record<string, unknown> = {}) {
    return {
        plan: 'PROFESSIONAL_MONTHLY',
        status: 'TRIAL',
        trialEndsAt: '2026-12-08T15:00:00Z',
        paidUntil: null,
        validUntil: '2026-12-08T15:00:00Z',
        offer: LAUNCH_OFFER,
        ...overrides,
    };
}

type Reply = { status: number; body?: unknown };
let replies: Map<string, Reply>;
let calls: string[];
const assign = vi.fn();

beforeEach(() => {
    replies = new Map();
    calls = [];
    assign.mockReset();
    vi.stubGlobal('location', { ...window.location, assign });
    vi.stubGlobal(
        'fetch',
        vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = input instanceof URL ? input.href : typeof input === 'string' ? input : input.url;
            const key = `${init?.method ?? 'GET'} ${url.replace(BASE, '') || '/'}`;
            calls.push(key);
            const reply = replies.get(key);
            if (!reply) return Promise.reject(new Error(`chamada inesperada: ${key}`));
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
                        <Route path="/app/assinatura" element={<SubscriptionPage now={NOW} />} />
                    </Routes>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

describe('tela de plano e assinatura (DEV-0102)', () => {
    it('em teste, mostra os dias que faltam, o preço de lançamento e o botão de assinar', async () => {
        replies.set('GET /', { status: 200, body: subscription() });
        renderAt('/app/assinatura');

        expect(await screen.findByText(/em teste grátis\. Faltam 10 dias/)).toBeInTheDocument();
        expect(
            screen.getByText(/R\$\s?150,00 por mês nos 3 primeiros meses, depois R\$\s?200,00 por mês\./),
        ).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Assinar com cartão' })).toBeInTheDocument();
    });

    it('ativa, mostra até quando vale e não oferece outro checkout', async () => {
        replies.set('GET /', {
            status: 200,
            body: subscription({
                status: 'ACTIVE',
                paidUntil: '2027-01-11T03:00:00Z',
                validUntil: '2027-01-11T03:00:00Z',
            }),
        });
        renderAt('/app/assinatura');

        expect(await screen.findByText(/ativa, até 11\/01\/2027/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Assinar com cartão' })).not.toBeInTheDocument();
    });

    it('vencida, avisa em texto que as prescrições ficam bloqueadas e mostra o preço cheio fora do lançamento', async () => {
        replies.set('GET /', {
            status: 200,
            body: subscription({
                status: 'EXPIRED',
                trialEndsAt: '2026-11-01T00:00:00Z',
                validUntil: '2026-11-01T00:00:00Z',
                offer: { value: 200, launchPrice: false, launchMonths: 3, regularValue: 200 },
            }),
        });
        renderAt('/app/assinatura');

        expect(await screen.findByRole('alert')).toHaveTextContent(
            /vencida\. Criar, editar e publicar fichas e planos fica bloqueado/,
        );
        expect(screen.getByText(/R\$\s?200,00 por mês\./)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Assinar com cartão' })).toBeInTheDocument();
    });

    it('assinar pede o checkout e leva ao link do Asaas', async () => {
        const user = userEvent.setup();
        replies.set('GET /', { status: 200, body: subscription() });
        replies.set('POST /checkout', {
            status: 201,
            body: {
                checkoutUrl: 'https://sandbox.asaas.com/checkoutSession/abc',
                value: 150,
                launchPrice: true,
            },
        });
        renderAt('/app/assinatura');

        await user.click(await screen.findByRole('button', { name: 'Assinar com cartão' }));

        await waitFor(() =>
            expect(assign).toHaveBeenCalledWith('https://sandbox.asaas.com/checkoutSession/abc'),
        );
        expect(calls).toContain('POST /checkout');
    });

    it('o erro do checkout aparece com a mensagem da API, sem sair da tela', async () => {
        const user = userEvent.setup();
        replies.set('GET /', { status: 200, body: subscription() });
        replies.set('POST /checkout', {
            status: 409,
            body: { detail: 'Já existe uma assinatura no Asaas para esta conta.' },
        });
        renderAt('/app/assinatura');

        await user.click(await screen.findByRole('button', { name: 'Assinar com cartão' }));

        expect(
            await screen.findByText('Já existe uma assinatura no Asaas para esta conta.'),
        ).toBeInTheDocument();
        expect(assign).not.toHaveBeenCalled();
    });

    it('o erro ao carregar a assinatura avisa e deixa tentar de novo', async () => {
        const user = userEvent.setup();
        replies.set('GET /', { status: 500, body: { detail: 'Falhou.' } });
        renderAt('/app/assinatura');

        expect(await screen.findByText(/Não foi possível carregar a assinatura/)).toBeInTheDocument();
        replies.set('GET /', { status: 200, body: subscription() });
        await user.click(screen.getByRole('button', { name: 'Tentar de novo' }));
        expect(await screen.findByText(/em teste grátis/)).toBeInTheDocument();
    });

    it('na volta com sucesso, avisa que a confirmação vem do Asaas, põe o foco no aviso e lê o estado de novo', async () => {
        replies.set('GET /', { status: 200, body: subscription() });
        renderAt('/app/assinatura?retorno=sucesso');

        const notice = await screen.findByText(/A confirmação chega do Asaas em instantes/);
        await waitFor(() => expect(notice).toHaveFocus());
        await waitFor(() =>
            expect(calls.filter((call) => call === 'GET /').length).toBeGreaterThanOrEqual(1),
        );
    });

    it('na volta cancelada, avisa que nada foi cobrado, com o foco no aviso', async () => {
        replies.set('GET /', { status: 200, body: subscription() });
        renderAt('/app/assinatura?retorno=cancelado');

        const notice = await screen.findByText('Pagamento cancelado. Nada foi cobrado.');
        await waitFor(() => expect(notice).toHaveFocus());
    });
});

describe('regras de texto da assinatura', () => {
    it('conta os dias que faltam, com o dia em curso como um dia inteiro e nunca negativo', () => {
        expect(daysLeft('2026-11-29T00:00:00Z', NOW)).toBe(1);
        expect(daysLeft('2026-11-28T15:00:00Z', NOW)).toBe(0);
        expect(daysLeft('2026-11-01T00:00:00Z', NOW)).toBe(0);
    });

    it('o preço de lançamento de 1 mês vira o 1º mês', () => {
        expect(offerText({ value: 150, launchPrice: true, launchMonths: 1, regularValue: 200 })).toMatch(
            /nos 1º mês/,
        );
    });
});
