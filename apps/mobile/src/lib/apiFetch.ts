import { InvalidSessionError } from '../features/auth/session';

export interface ApiFetchOptions {
    /** Access token válido; `force` pede um token novo (depois de um 401). */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** A API recusou até o token renovado: a sessão local é encerrada. */
    onSessionRejected: () => Promise<void>;
    /** Endereços base das APIs do FitHub, com o caminho: só eles e o que fica abaixo deles recebem o token. */
    allowedBases: readonly string[];
    fetch?: typeof fetch;
}

export type ApiFetch = (url: string, init?: RequestInit) => Promise<Response>;

/**
 * URL absoluta, ou `undefined` se não for (relativa, inválida). Outro esquema (`fithub://`, `javascript:`) não precisa de
 * filtro aqui: a origem dele nunca é a de uma base http(s), e a comparação abaixo já o recusa.
 */
function parseUrl(url: string): URL | undefined {
    try {
        return new URL(url);
    } catch {
        return undefined;
    }
}

/** Mesma origem de uma base e caminho igual ou abaixo dela; `..` já vem resolvido pelo `URL`. */
function isInsideAnyBase(target: URL, bases: readonly string[]): boolean {
    return bases.some((base) => {
        const allowed = parseUrl(base);
        if (!allowed || allowed.origin !== target.origin) return false;
        const prefix = allowed.pathname.replace(/\/$/, '');
        return target.pathname === prefix || target.pathname.startsWith(`${prefix}/`);
    });
}

/**
 * `fetch` autenticado para as APIs do FitHub (DEV-0028): manda o Bearer token e, num 401, renova o
 * token e tenta mais uma vez. Se a API recusar de novo, encerra a sessão e lança
 * {@link InvalidSessionError}. O token nunca vai para uma URL fora dos endereços de `allowedBases` (DEV-0178).
 */
export function createApiFetch(options: ApiFetchOptions): ApiFetch {
    const send = options.fetch ?? fetch;

    return async (url, init) => {
        const target = parseUrl(url);
        if (!target || !isInsideAnyBase(target, options.allowedBases)) {
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
