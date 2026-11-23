import * as AuthSession from 'expo-auth-session';

import { createExpoAuthGateway } from './expoAuthGateway';
import { InvalidSessionError } from './session';

// O navegador do sistema e o Keycloak são simulados: aqui se confere o que o app pede e como reage.
jest.mock('expo-auth-session', () => {
    class TokenError extends Error {
        code: string;
        constructor(code: string) {
            super(code);
            this.code = code;
        }
    }
    const requests: unknown[] = [];
    const prompt = jest.fn();
    class AuthRequest {
        codeVerifier = 'verificador-do-pkce';
        constructor(mockConfig: unknown) {
            requests.push(mockConfig);
        }
        promptAsync = prompt;
    }
    return {
        __esModule: true,
        AuthRequest,
        TokenError,
        CodeChallengeMethod: { S256: 'S256', Plain: 'plain' },
        ResponseType: { Code: 'code', Token: 'token' },
        exchangeCodeAsync: jest.fn(),
        refreshAsync: jest.fn(),
        __requests: requests,
        __prompt: prompt,
    };
});

const mocked = AuthSession as unknown as {
    __requests: Record<string, unknown>[];
    __prompt: jest.Mock;
    exchangeCodeAsync: jest.Mock;
    refreshAsync: jest.Mock;
    TokenError: new (code: string) => Error;
};
const ENDPOINTS = {
    authorizationEndpoint: 'http://localhost:8080/realms/fithub/protocol/openid-connect/auth',
    tokenEndpoint: 'http://localhost:8080/realms/fithub/protocol/openid-connect/token',
    endSessionEndpoint: 'http://localhost:8080/realms/fithub/protocol/openid-connect/logout',
};
const response = (overrides = {}) => ({
    accessToken: 'access',
    refreshToken: 'refresh',
    issuedAt: 1000,
    expiresIn: 300,
    ...overrides,
});

beforeEach(() => {
    mocked.__requests.length = 0;
    mocked.__prompt.mockReset();
    mocked.exchangeCodeAsync.mockReset();
    mocked.refreshAsync.mockReset();
});

describe('login', () => {
    it('pede Authorization Code com PKCE S256 para o client do app e troca o código com o verificador', async () => {
        mocked.__prompt.mockResolvedValue({ type: 'success', params: { code: 'codigo', state: 's' } });
        mocked.exchangeCodeAsync.mockResolvedValue(response());

        const tokens = await createExpoAuthGateway().signIn();

        expect(mocked.__requests).toEqual([
            {
                clientId: 'fithub-mobile',
                redirectUri: 'fithub://auth',
                scopes: ['openid', 'offline_access'],
                responseType: 'code',
                usePKCE: true,
                codeChallengeMethod: 'S256',
            },
        ]);
        expect(mocked.__prompt).toHaveBeenCalledWith(ENDPOINTS);
        expect(mocked.exchangeCodeAsync).toHaveBeenCalledWith(
            {
                clientId: 'fithub-mobile',
                code: 'codigo',
                redirectUri: 'fithub://auth',
                extraParams: { code_verifier: 'verificador-do-pkce' },
            },
            ENDPOINTS,
        );
        expect(tokens).toEqual({ accessToken: 'access', refreshToken: 'refresh', expiresAt: 1_300_000 });
    });

    it.each(['cancel', 'dismiss'])('fechar o navegador (%s) não é erro e não troca código', async (type) => {
        mocked.__prompt.mockResolvedValue({ type });

        await expect(createExpoAuthGateway().signIn()).resolves.toBeUndefined();
        expect(mocked.exchangeCodeAsync).not.toHaveBeenCalled();
    });

    it.each([
        ['state diferente do enviado', { type: 'error', params: { error: 'state_mismatch' } }],
        ['erro do Keycloak', { type: 'error', params: { error: 'access_denied' } }],
        ['sucesso sem código', { type: 'success', params: {} }],
        ['resultado inesperado', { type: 'locked' }],
    ])('%s não troca código e falha', async (_case, result) => {
        mocked.__prompt.mockResolvedValue(result);

        await expect(createExpoAuthGateway().signIn()).rejects.toThrow('O login não foi concluído.');
        expect(mocked.exchangeCodeAsync).not.toHaveBeenCalled();
    });

    it('resposta sem refresh token não vira sessão', async () => {
        mocked.__prompt.mockResolvedValue({ type: 'success', params: { code: 'codigo' } });
        mocked.exchangeCodeAsync.mockResolvedValue(response({ refreshToken: undefined }));

        await expect(createExpoAuthGateway().signIn()).rejects.toThrow('Resposta de token incompleta.');
    });
});

describe('renovação', () => {
    it('troca o refresh token e mantém o anterior quando o Keycloak não devolve um novo', async () => {
        mocked.refreshAsync.mockResolvedValue(response({ refreshToken: undefined, accessToken: 'novo' }));

        const tokens = await createExpoAuthGateway().refresh('refresh-atual');

        expect(mocked.refreshAsync).toHaveBeenCalledWith(
            { clientId: 'fithub-mobile', refreshToken: 'refresh-atual' },
            ENDPOINTS,
        );
        expect(tokens).toEqual({ accessToken: 'novo', refreshToken: 'refresh-atual', expiresAt: 1_300_000 });
    });

    it('invalid_grant vira InvalidSessionError', async () => {
        mocked.refreshAsync.mockRejectedValue(new mocked.TokenError('invalid_grant'));

        await expect(createExpoAuthGateway().refresh('refresh')).rejects.toBeInstanceOf(InvalidSessionError);
    });

    it.each([
        ['erro de rede', () => new TypeError('Network request failed')],
        ['erro do servidor', () => new mocked.TokenError('server_error')],
    ])('%s não é tratado como sessão inválida', async (_case, error) => {
        mocked.refreshAsync.mockRejectedValue(error());

        const failure: unknown = await createExpoAuthGateway()
            .refresh('refresh')
            .catch((caught: unknown) => caught);
        expect(failure).toBeInstanceOf(Error);
        expect(failure).not.toBeInstanceOf(InvalidSessionError);
    });
});

describe('logout', () => {
    it('revoga a sessão no Keycloak pelo refresh token, sem abrir o navegador', async () => {
        const send = jest.fn(() => Promise.resolve({ ok: true, status: 204 } as Response));

        await createExpoAuthGateway(send as unknown as typeof fetch).signOut('refresh com espaço&x');

        expect(send).toHaveBeenCalledWith(ENDPOINTS.endSessionEndpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'client_id=fithub-mobile&refresh_token=refresh%20com%20espa%C3%A7o%26x',
        });
    });

    it('recusa do Keycloak é erro, e a mensagem não leva o token', async () => {
        const send = jest.fn(() => Promise.resolve({ ok: false, status: 400 } as Response));

        const failure = await createExpoAuthGateway(send as unknown as typeof fetch)
            .signOut('refresh-secreto')
            .catch((caught: Error) => caught);
        expect(failure).toBeInstanceOf(Error);
        expect((failure as Error).message).not.toContain('refresh-secreto');
    });
});
