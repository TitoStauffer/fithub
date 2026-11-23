import { SessionExpiredError } from '@/features/auth/authContext';

export interface ApiFetchOptions {
    /** Token de acesso válido; `force` pede um token novo (depois de um 401). */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** A API recusou até o token renovado: a sessão local é encerrada. */
    onSessionRejected: () => void;
    /** Origens das APIs do FitHub: as únicas que recebem o token. */
    allowedOrigins: readonly string[];
    fetch?: typeof fetch;
}

export type ApiFetch = (url: string, init?: RequestInit) => Promise<Response>;

/** URL absoluta ou relativa à página; `undefined` se não for válida. Sem `URL.parse`, que é de 2024. */
function parseUrl(url: string): URL | undefined {
    try {
        return new URL(url, window.location.origin);
    } catch {
        return undefined;
    }
}

/**
 * `fetch` autenticado para as APIs do FitHub (DEV-0027): manda o Bearer token e, num 401, renova o
 * token e tenta mais uma vez. Se a API recusar de novo, encerra a sessão e lança
 * {@link SessionExpiredError}. O token nunca vai para uma origem fora de `allowedOrigins`.
 */
export function createApiFetch(options: ApiFetchOptions): ApiFetch {
    const send = options.fetch ?? fetch;

    return async (url, init) => {
        // URL que nem é válida também é recusada antes de qualquer token ser pedido.
        const target = parseUrl(url);
        if (!target || !options.allowedOrigins.includes(target.origin)) {
            throw new Error(`Chamada autenticada fora das APIs do FitHub: ${target?.origin ?? url}`);
        }
        const request = (token: string) => {
            const headers = new Headers(init?.headers);
            headers.set('Authorization', `Bearer ${token}`);
            return send(target, { ...init, headers });
        };

        const first = await request(await options.getAccessToken());
        if (first.status !== 401) return first;

        const retry = await request(await options.getAccessToken({ force: true }));
        if (retry.status !== 401) return retry;

        options.onSessionRejected();
        throw new SessionExpiredError();
    };
}
