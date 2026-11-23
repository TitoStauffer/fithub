import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createKeycloakClient, RefreshRejectedError } from './authClient';

// O keycloak-js de verdade precisa do Keycloak no ar; aqui só importa como o adaptador classifica as
// falhas de renovação (DEV-0146).
interface KeycloakStub {
    updateToken: ReturnType<typeof vi.fn<(minValidity: number) => Promise<boolean>>>;
    isTokenExpired: ReturnType<typeof vi.fn<(minValidity?: number) => boolean>>;
    token: string | undefined;
    refreshToken: string | undefined;
}

const keycloak = vi.hoisted((): KeycloakStub => ({
    updateToken: vi.fn<(minValidity: number) => Promise<boolean>>(),
    isTokenExpired: vi.fn<(minValidity?: number) => boolean>(),
    token: 'token-atual',
    refreshToken: 'refresh-atual',
}));

vi.mock('keycloak-js', () => ({
    default: class {
        get token() {
            return keycloak.token;
        }
        get refreshToken() {
            return keycloak.refreshToken;
        }
        updateToken = keycloak.updateToken;
        isTokenExpired = keycloak.isTokenExpired;
    },
}));

beforeEach(() => {
    keycloak.updateToken.mockReset();
    keycloak.isTokenExpired.mockReset();
    keycloak.token = 'token-atual';
    keycloak.refreshToken = 'refresh-atual';
});

describe('renovação do token pelo keycloak-js', () => {
    it.each([400, 401])('resposta %i do endpoint de token é recusa da sessão', async (status) => {
        keycloak.updateToken.mockRejectedValue(
            Object.assign(new Error('Failed to refresh'), { response: { status } }),
        );

        await expect(createKeycloakClient().refresh(60)).rejects.toBeInstanceOf(RefreshRejectedError);
    });

    it('sem refresh token é recusa da sessão', async () => {
        keycloak.refreshToken = undefined;
        keycloak.updateToken.mockRejectedValue(
            new Error('Unable to update token, no refresh token available.'),
        );

        await expect(createKeycloakClient().refresh(60)).rejects.toBeInstanceOf(RefreshRejectedError);
    });

    it.each([
        ['rede fora', () => new TypeError('Failed to fetch')],
        [
            'erro do servidor',
            () => Object.assign(new Error('Failed to refresh'), { response: { status: 503 } }),
        ],
    ])('%s é falha passageira, não recusa', async (_case, error) => {
        keycloak.updateToken.mockRejectedValue(error());

        const failure: unknown = await createKeycloakClient()
            .refresh(60)
            .catch((caught: unknown) => caught);
        expect(failure).toBeInstanceOf(Error);
        expect(failure).not.toBeInstanceOf(RefreshRejectedError);
    });

    it('token válido é o que existe e ainda não venceu', () => {
        const client = createKeycloakClient();
        keycloak.isTokenExpired.mockReturnValue(false);
        expect(client.hasValidToken()).toBe(true);

        keycloak.isTokenExpired.mockReturnValue(true);
        expect(client.hasValidToken()).toBe(false);

        keycloak.isTokenExpired.mockReturnValue(false);
        keycloak.token = undefined;
        expect(client.hasValidToken()).toBe(false);
    });
});
