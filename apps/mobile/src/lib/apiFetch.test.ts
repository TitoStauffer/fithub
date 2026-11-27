import { apiBases } from '../features/auth/authConfig';
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
        allowedBases: [API],
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

// Em produção, as APIs ficam em caminhos do mesmo domínio do Keycloak e dos buckets das fotos (DEV-0178, ADR-014).
describe('endereço base com caminho', () => {
    it('o token vai só para baixo do caminho da API', async () => {
        const sent: string[] = [];
        const apiFetch = createApiFetch({
            getAccessToken: () => Promise.resolve('atual'),
            onSessionRejected: () => Promise.resolve(),
            allowedBases: ['https://fithub.example/api/client'],
            fetch: jest.fn((input: RequestInfo | URL) => {
                sent.push(String(input));
                return Promise.resolve({ status: 200 } as Response);
            }) as unknown as typeof fetch,
        });

        await apiFetch('https://fithub.example/api/client/clients/me');
        await apiFetch('https://fithub.example/api/client');
        for (const outside of [
            'https://fithub.example/auth/realms/fithub/protocol/openid-connect/userinfo',
            'https://fithub.example/fithub-photos/foto.jpg',
            'https://fithub.example/api/clients-falso/x',
            'https://fithub.example/api/client/../../auth/x',
            'https://outro.example/api/client/clients/me',
        ]) {
            await expect(apiFetch(outside)).rejects.toThrow('fora das APIs do FitHub');
        }
        expect(sent).toEqual([
            'https://fithub.example/api/client/clients/me',
            'https://fithub.example/api/client',
        ]);
    });
});

describe('URL enviada', () => {
    it('é a mesma que o filtro validou, já normalizada', async () => {
        const sent: string[] = [];
        const apiFetch = createApiFetch({
            getAccessToken: () => Promise.resolve('atual'),
            onSessionRejected: () => Promise.resolve(),
            allowedBases: ['https://fithub.example/api/client'],
            fetch: jest.fn((input: RequestInfo | URL) => {
                sent.push(String(input));
                return Promise.resolve({ status: 200 } as Response);
            }) as unknown as typeof fetch,
        });

        await apiFetch('https://fithub.example/api/client/x/../clients/me');

        expect(sent).toEqual(['https://fithub.example/api/client/clients/me']);
    });
});

describe('apiBases', () => {
    // Com os endereços de produção (caminhos no mesmo domínio), a base guarda o caminho, não só a origem.
    it('com os endereços de produção, guarda o caminho de cada API', () => {
        const keys = [
            'EXPO_PUBLIC_CLIENT_API_URL',
            'EXPO_PUBLIC_TRAINING_API_URL',
            'EXPO_PUBLIC_NUTRITION_API_URL',
        ];
        // Guarda o valor anterior de cada variável para devolvê-lo no fim, sem mudar os testes seguintes.
        const previous = keys.map((key) => process.env[key]);
        process.env.EXPO_PUBLIC_CLIENT_API_URL = 'https://fithub.example/api/client';
        process.env.EXPO_PUBLIC_TRAINING_API_URL = 'https://fithub.example/api/training';
        process.env.EXPO_PUBLIC_NUTRITION_API_URL = 'https://fithub.example/api/nutrition';
        try {
            expect(apiBases()).toEqual([
                'https://fithub.example/api/client',
                'https://fithub.example/api/training',
                'https://fithub.example/api/nutrition',
            ]);
        } finally {
            keys.forEach((key, i) => {
                if (previous[i] === undefined) delete process.env[key];
                else process.env[key] = previous[i];
            });
        }
    });

    it('o token vai só para o client-service, o training-service e o nutrition-service', () => {
        expect(apiBases()).toEqual([
            'http://localhost:8082',
            'http://localhost:8083',
            'http://localhost:8084',
        ]);
    });
});
