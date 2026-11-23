import { ApiProblemError, OfflineError, fetchMyProfile, saveMyHealth } from './api';
import { InvalidSessionError } from '../features/auth/session';

jest.mock('expo-secure-store', () => ({ __esModule: true }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));

const BASE = 'http://api.test';

function json(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api do client-service', () => {
    it('perfil que ainda não existe volta null', async () => {
        await expect(fetchMyProfile(async () => new Response(null, { status: 404 }), BASE)).resolves.toBeNull();
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

        const error = await saveMyHealth(invalid, { conditions: [] }, BASE).catch((failure: unknown) => failure);

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
