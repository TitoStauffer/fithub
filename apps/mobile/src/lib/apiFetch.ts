import { originOf } from '../features/auth/authConfig';
import { InvalidSessionError } from '../features/auth/session';

export interface ApiFetchOptions {
    /** Access token válido; `force` pede um token novo (depois de um 401). */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** A API recusou até o token renovado: a sessão local é encerrada. */
    onSessionRejected: () => Promise<void>;
    /** Origens das APIs do FitHub: as únicas que recebem o token. */
    allowedOrigins: readonly string[];
    fetch?: typeof fetch;
}

export type ApiFetch = (url: string, init?: RequestInit) => Promise<Response>;

/**
 * `fetch` autenticado para as APIs do FitHub (DEV-0028): manda o Bearer token e, num 401, renova o
 * token e tenta mais uma vez. Se a API recusar de novo, encerra a sessão e lança
 * {@link InvalidSessionError}. O token nunca vai para uma origem fora de `allowedOrigins`.
 */
export function createApiFetch(options: ApiFetchOptions): ApiFetch {
    const send = options.fetch ?? fetch;

    return async (url, init) => {
        const origin = originOf(url);
        if (origin === '' || !options.allowedOrigins.includes(origin)) {
            throw new Error('Chamada autenticada fora das APIs do FitHub.');
        }
        const request = (token: string) => {
            const headers = new Headers(init?.headers);
            headers.set('Authorization', `Bearer ${token}`);
            return send(url, { ...init, headers });
        };

        const first = await request(await options.getAccessToken());
        if (first.status !== 401) return first;

        const retry = await request(await options.getAccessToken({ force: true }));
        if (retry.status !== 401) return retry;

        await options.onSessionRejected();
        throw new InvalidSessionError();
    };
}
