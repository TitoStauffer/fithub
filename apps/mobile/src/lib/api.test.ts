import {
    ApiProblemError,
    OfflineError,
    confirmMealPhoto,
    fetchMealPlanToday,
    fetchMyBodySeries,
    fetchMyDietProgress,
    fetchMyTrainingProgress,
    fetchMyProfile,
    logMeal,
    nutritionUrl,
    putToPresignedUrl,
    requestMealPhotoUpload,
    saveMyHealth,
} from './api';
import { InvalidSessionError } from '../features/auth/session';

jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

const BASE = 'http://api.test';

function json(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api do client-service', () => {
    it('perfil que ainda não existe volta null', async () => {
        await expect(
            fetchMyProfile(async () => new Response(null, { status: 404 }), BASE),
        ).resolves.toBeNull();
    });

    it('falha de rede vira OfflineError, para a tela avisar e manter o que foi digitado', async () => {
        const offline = async () => {
            throw new TypeError('Network request failed');
        };

        await expect(fetchMyProfile(offline, BASE)).rejects.toBeInstanceOf(OfflineError);
    });

    it('sessão recusada continua sendo sessão recusada', async () => {
        const rejected = async () => {
            throw new InvalidSessionError();
        };

        await expect(fetchMyProfile(rejected, BASE)).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it('erro da API traz o detail e a mensagem de cada campo', async () => {
        const invalid = async () =>
            json(400, {
                detail: 'Um ou mais campos são inválidos.',
                errors: [{ field: 'notes', message: 'O texto livre pode ter até 1000 caracteres.' }],
            });

        const error = await saveMyHealth(invalid, { conditions: [] }, BASE).catch(
            (failure: unknown) => failure,
        );

        expect(error).toBeInstanceOf(ApiProblemError);
        expect((error as ApiProblemError).status).toBe(400);
        expect((error as ApiProblemError).fieldErrors).toEqual({
            notes: 'O texto livre pode ter até 1000 caracteres.',
        });
    });

    it('grava com PUT e JSON', async () => {
        const calls: { url: string; init?: RequestInit }[] = [];
        await saveMyHealth(
            async (url, init) => {
                calls.push({ url, init });
                return json(200, { conditions: [], updatedAt: '2026-11-23T12:00:00Z' });
            },
            { conditions: ['DIABETES'] },
            BASE,
        );

        expect(calls[0]?.url).toBe(`${BASE}/clients/me/health`);
        expect(calls[0]?.init?.method).toBe('PUT');
        expect(calls[0]?.init?.body).toBe('{"conditions":["DIABETES"]}');
    });
});

describe('envio da foto para a URL pré-assinada (DEV-0050)', () => {
    it('vai direto ao armazenamento com o tipo do arquivo, sem o token da sessão', async () => {
        const fetchImpl = jest.fn(
            async (_url: string, _init?: RequestInit) => new Response(null, { status: 200 }),
        );
        const file = new Blob(['x'], { type: 'image/jpeg' });

        await putToPresignedUrl(
            'https://fotos.example/put',
            file,
            'image/jpeg',
            fetchImpl as unknown as typeof fetch,
        );

        const [url, init] = fetchImpl.mock.calls[0]!;
        expect(url).toBe('https://fotos.example/put');
        expect(init?.method).toBe('PUT');
        expect(init?.headers).toEqual({ 'Content-Type': 'image/jpeg' });
        expect(init?.body).toBe(file);
    });

    it('falha de rede vira OfflineError e recusa do armazenamento vira erro', async () => {
        const file = new Blob(['x']);
        const offline = (async () => {
            throw new TypeError('Network request failed');
        }) as unknown as typeof fetch;
        const refused = (async () => new Response(null, { status: 403 })) as unknown as typeof fetch;

        await expect(
            putToPresignedUrl('https://fotos.example/put', file, 'image/png', offline),
        ).rejects.toBeInstanceOf(OfflineError);
        await expect(
            putToPresignedUrl('https://fotos.example/put', file, 'image/png', refused),
        ).rejects.toBeInstanceOf(ApiProblemError);
    });
});

describe('api do nutrition-service', () => {
    /** Guarda o método e o endereço de cada chamada e responde 200 com um corpo vazio. */
    function recorder() {
        const calls: { method: string; url: string; body?: unknown }[] = [];
        const apiFetch = async (url: string, init?: RequestInit) => {
            calls.push({
                method: init?.method ?? 'GET',
                url,
                body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
            });
            return json(200, {});
        };
        return { calls, apiFetch };
    }

    it('chama as rotas do contrato, com o método e o id codificado', async () => {
        const { calls, apiFetch } = recorder();
        await fetchMealPlanToday(apiFetch, BASE);
        await logMeal(apiFetch, 'id 1', { planId: 'mp-1', mealPosition: 0, status: 'DONE' }, BASE);
        await requestMealPhotoUpload(apiFetch, 'id 1', 'image/jpeg', BASE);
        await confirmMealPhoto(apiFetch, 'id 1', 'u-1', BASE);

        expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
            'GET http://api.test/me/meal-plan/today',
            'PUT http://api.test/me/meal-logs/id%201',
            'POST http://api.test/me/meal-logs/id%201/photo/upload-url',
            'PUT http://api.test/me/meal-logs/id%201/photo',
        ]);
        expect(calls[2]?.body).toEqual({ contentType: 'image/jpeg' });
        expect(calls[3]?.body).toEqual({ uploadId: 'u-1' });
    });

    it('sem plano publicado, as refeições de hoje voltam null', async () => {
        await expect(
            fetchMealPlanToday(async () => new Response(null, { status: 404 }), BASE),
        ).resolves.toBeNull();
    });

    it('a rota vem do contrato gerado: rota ou método que não existem não compilam', () => {
        expect(nutritionUrl(BASE, 'delete', '/me/meal-logs/{logId}/photo', { logId: 'x' })).toBe(
            'http://api.test/me/meal-logs/x/photo',
        );
        // @ts-expect-error a rota não existe no OpenAPI do nutrition-service
        nutritionUrl(BASE, 'put', '/me/meal-log/{logId}', { logId: 'x' });
        // @ts-expect-error a rota existe, mas não aceita POST
        nutritionUrl(BASE, 'post', '/me/meal-logs/{logId}', { logId: 'x' });
    });
});

describe('api da evolução (DEV-0096)', () => {
    it('pede o treino, a dieta e o corpo do próprio cliente nas rotas do contrato', async () => {
        const urls: string[] = [];
        const capture = async (url: string) => {
            urls.push(url);
            return json(200, {});
        };
        await fetchMyTrainingProgress(capture, '2026-10-29', BASE);
        await fetchMyDietProgress(capture, '2026-10-29', BASE);
        await fetchMyBodySeries(capture, BASE);
        expect(urls).toEqual([
            `${BASE}/me/progress/training?from=2026-10-29`,
            `${BASE}/me/progress/nutrition?from=2026-10-29`,
            `${BASE}/clients/me/assessments/series`,
        ]);
    });
});
