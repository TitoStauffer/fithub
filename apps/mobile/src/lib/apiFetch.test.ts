import { apiOrigins, originOf } from '../features/auth/authConfig';
import { InvalidSessionError } from '../features/auth/session';
import { createApiFetch } from './apiFetch';

const API = 'http://localhost:8082';

function setup(statuses: number[]) {
    const sent: { url: string; authorization: string | null }[] = [];
    const fetchFake = jest.fn((input: RequestInfo | URL, init?: RequestInit) => {
        sent.push({ url: String(input), authorization: new Headers(init?.headers).get('Authorization') });
        return Promise.resolve({ status: statuses[sent.length - 1] ?? 200 } as Response);
    });
    const getAccessToken = jest.fn((options?: { force?: boolean }) =>
        Promise.resolve(options?.force ? 'novo' : 'atual'),
    );
    const onSessionRejected = jest.fn(() => Promise.resolve());
    const apiFetch = createApiFetch({
        getAccessToken,
        onSessionRejected,
        allowedOrigins: [API],
        fetch: fetchFake as unknown as typeof fetch,
    });
    return { apiFetch, sent, getAccessToken, onSessionRejected };
}

describe('createApiFetch', () => {
    it('manda o Bearer token para a API do FitHub', async () => {
        const { apiFetch, sent } = setup([200]);
        const response = await apiFetch(`${API}/clients/me`);

        expect(response.status).toBe(200);
        expect(sent).toEqual([{ url: `${API}/clients/me`, authorization: 'Bearer atual' }]);
    });

    it('num 401, renova o token e tenta mais uma vez', async () => {
        const { apiFetch, sent, getAccessToken, onSessionRejected } = setup([401, 200]);
        const response = await apiFetch(`${API}/clients/me`);

        expect(response.status).toBe(200);
        expect(sent.map(({ authorization }) => authorization)).toEqual(['Bearer atual', 'Bearer novo']);
        expect(getAccessToken).toHaveBeenLastCalledWith({ force: true });
        expect(onSessionRejected).not.toHaveBeenCalled();
    });

    it('se a API recusa também o token novo, encerra a sessão e não tenta de novo', async () => {
        const { apiFetch, sent, onSessionRejected } = setup([401, 401, 200]);

        await expect(apiFetch(`${API}/clients/me`)).rejects.toBeInstanceOf(InvalidSessionError);
        expect(sent).toHaveLength(2);
        expect(onSessionRejected).toHaveBeenCalledTimes(1);
    });

    it('erro que não é 401 volta como está, sem renovar nem encerrar a sessão', async () => {
        const { apiFetch, sent, onSessionRejected } = setup([403]);
        const response = await apiFetch(`${API}/clients/me`);

        expect(response.status).toBe(403);
        expect(sent).toHaveLength(1);
        expect(onSessionRejected).not.toHaveBeenCalled();
    });

    it.each([
        'https://evil.example/collect',
        'http://localhost:9999/x',
        '//evil.example/x',
        '/clients/me',
        'http://localhost:8082.evil.example/x',
        'http://localhost:8082@evil.example/x',
        'fithub://auth',
        'nada',
    ])('não manda o token para fora das APIs do FitHub (%s)', async (url) => {
        const { apiFetch, sent, getAccessToken } = setup([200]);

        await expect(apiFetch(url)).rejects.toThrow('fora das APIs do FitHub');
        expect(sent).toEqual([]);
        expect(getAccessToken).not.toHaveBeenCalled();
    });
});

describe('originOf', () => {
    it('devolve a origem de URL http(s) absoluta e vazio para o resto', () => {
        expect(originOf('http://localhost:8082/clients/me?x=1')).toBe('http://localhost:8082');
        expect(originOf('HTTPS://API.FitHub.app/x')).toBe('https://api.fithub.app');
        expect(originOf('http://localhost:8082@evil.example/x')).toBe('http://evil.example');
        for (const url of ['', '/x', '//evil.example', 'fithub://auth', 'javascript:alert(1)', 'nada']) {
            expect(originOf(url)).toBe('');
        }
    });
});

describe('apiOrigins', () => {
    it('o token vai só para o client-service e o training-service', () => {
        expect(apiOrigins()).toEqual([
            'http://localhost:8082',
            'http://localhost:8083',
            'http://localhost:8084',
        ]);
    });
});
