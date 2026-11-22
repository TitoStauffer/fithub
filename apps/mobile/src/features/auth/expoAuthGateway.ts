import {
    AuthRequest,
    CodeChallengeMethod,
    ResponseType,
    TokenError,
    exchangeCodeAsync,
    refreshAsync,
    type TokenResponse,
} from 'expo-auth-session';

import { authConfig } from './authConfig';
import { InvalidSessionError, type AuthGateway, type Tokens } from './session';

/** Validade assumida quando o Keycloak não informa `expires_in` (o access token do realm dura 5 minutos). */
const DEFAULT_EXPIRES_IN_SECONDS = 300;

function toTokens(response: TokenResponse, previousRefreshToken?: string): Tokens {
    // Sem rotação de refresh token, o Keycloak pode não devolver um novo: o anterior continua valendo.
    const refreshToken = response.refreshToken ?? previousRefreshToken;
    if (!response.accessToken || !refreshToken) throw new Error('Resposta de token incompleta.');
    return {
        accessToken: response.accessToken,
        refreshToken,
        expiresAt: (response.issuedAt + (response.expiresIn ?? DEFAULT_EXPIRES_IN_SECONDS)) * 1000,
    };
}

/**
 * Login do app com o `expo-auth-session` (DEV-0028): Authorization Code + PKCE S256 no navegador do
 * sistema, voltando pelo esquema `fithub://`. O `state` é gerado e conferido pela biblioteca.
 */
export function createExpoAuthGateway(send: typeof fetch = fetch): AuthGateway {
    return {
        async signIn() {
            const request = new AuthRequest({
                clientId: authConfig.clientId,
                redirectUri: authConfig.redirectUri,
                scopes: [...authConfig.scopes],
                responseType: ResponseType.Code,
                usePKCE: true,
                codeChallengeMethod: CodeChallengeMethod.S256,
            });
            const result = await request.promptAsync(authConfig.endpoints);
            // Fechou o navegador sem entrar: não é erro.
            if (result.type === 'cancel' || result.type === 'dismiss') return undefined;
            // `state` diferente do enviado chega aqui como erro (state_mismatch).
            if (result.type !== 'success' || !result.params.code || !request.codeVerifier) {
                throw new Error('O login não foi concluído.');
            }
            const response = await exchangeCodeAsync(
                {
                    clientId: authConfig.clientId,
                    code: result.params.code,
                    redirectUri: authConfig.redirectUri,
                    extraParams: { code_verifier: request.codeVerifier },
                },
                authConfig.endpoints,
            );
            return toTokens(response);
        },
        async refresh(refreshToken) {
            try {
                const response = await refreshAsync(
                    { clientId: authConfig.clientId, refreshToken },
                    authConfig.endpoints,
                );
                return toTokens(response, refreshToken);
            } catch (error) {
                // invalid_grant: refresh token vencido, revogado ou de sessão encerrada.
                if (error instanceof TokenError && error.code === 'invalid_grant')
                    throw new InvalidSessionError();
                throw error;
            }
        },
        async signOut(refreshToken) {
            // Logout do Keycloak por refresh token: encerra a sessão (inclusive offline) sem abrir o navegador.
            const response = await send(authConfig.endpoints.endSessionEndpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `client_id=${encodeURIComponent(authConfig.clientId)}&refresh_token=${encodeURIComponent(refreshToken)}`,
            });
            if (!response.ok) throw new Error(`Logout recusado pelo Keycloak (${response.status}).`);
        },
    };
}
