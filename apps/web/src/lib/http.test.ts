import { describe, expect, it, vi } from 'vitest';

import { SessionExpiredError } from '@/features/auth/authContext';

import { createApiFetch } from './http';

const API = 'http://localhost:8081';

function setup(statuses: number[]) {
    const sent: { url: string; authorization: string | null }[] = [];
    const fetchFake = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = input instanceof Request ? input.url : String(input);
        sent.push({ url, authorization: new Headers(init?.headers).get('Authorization') });
        return Promise.resolve(new Response(null, { status: statuses[sent.length - 1] ?? 200 }));
    });
    const getAccessToken = vi.fn((options?: { force?: boolean }) =>
        Promise.resolve(options?.force ? 'novo' : 'atual'),
    );
    const onSessionRejected = vi.fn();
    const apiFetch = createApiFetch({
        getAccessToken,
        onSessionRejected,
        allowedOrigins: [API],
        fetch: fetchFake,
    });
    return { apiFetch, sent, getAccessToken, onSessionRejected };
}

describe('createApiFetch', () => {
    it('manda o Bearer token e preserva os outros cabeçalhos', async () => {
        const { apiFetch, sent } = setup([200]);
        const response = await apiFetch(`${API}/professionals/me`, {
            headers: { Accept: 'application/json' },
        });

        expect(response.status).toBe(200);
        expect(sent).toEqual([{ url: `${API}/professionals/me`, authorization: 'Bearer atual' }]);
    });

    it('num 401, renova o token e tenta mais uma vez', async () => {
        const { apiFetch, sent, getAccessToken, onSessionRejected } = setup([401, 200]);
        const response = await apiFetch(`${API}/professionals/me`);

        expect(response.status).toBe(200);
        expect(sent.map(({ authorization }) => authorization)).toEqual(['Bearer atual', 'Bearer novo']);
        expect(getAccessToken).toHaveBeenLastCalledWith({ force: true });
        expect(onSessionRejected).not.toHaveBeenCalled();
    });

    it('se a API recusa também o token novo, encerra a sessão e não tenta de novo', async () => {
        const { apiFetch, sent, onSessionRejected } = setup([401, 401, 200]);

        await expect(apiFetch(`${API}/professionals/me`)).rejects.toBeInstanceOf(SessionExpiredError);
        expect(sent).toHaveLength(2);
        expect(onSessionRejected).toHaveBeenCalledTimes(1);
    });

    it('erro que não é 401 volta como está, sem renovar nem encerrar a sessão', async () => {
        const { apiFetch, sent, onSessionRejected } = setup([403]);
        const response = await apiFetch(`${API}/professionals/me`);

        expect(response.status).toBe(403);
        expect(sent).toHaveLength(1);
        expect(onSessionRejected).not.toHaveBeenCalled();
    });

    it.each([
        'https://evil.example/collect',
        'http://localhost:9999/x',
        '//evil.example/x',
        'http://localhost:8081.evil.example/x',
        'http://[::1',
    ])('não manda o token para fora das APIs do FitHub (%s)', async (url) => {
        const { apiFetch, sent, getAccessToken } = setup([200]);

        await expect(apiFetch(url)).rejects.toThrow('fora das APIs do FitHub');
        expect(sent).toEqual([]);
        expect(getAccessToken).not.toHaveBeenCalled();
    });

    it('falha ao obter o token não chega a chamar a API', async () => {
        const { sent } = setup([200]);
        const apiFetch = createApiFetch({
            getAccessToken: () => Promise.reject(new SessionExpiredError()),
            onSessionRejected: vi.fn(),
            allowedOrigins: [API],
            fetch: vi.fn(),
        });

        await expect(apiFetch(`${API}/professionals/me`)).rejects.toBeInstanceOf(SessionExpiredError);
        expect(sent).toEqual([]);
    });
});
