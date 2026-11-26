import { SessionExpiredError } from '@/features/auth/authContext';

export interface ApiFetchOptions {
    /** Token de acesso válido; `force` pede um token novo (depois de um 401). */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** A API recusou até o token renovado: a sessão vai para o estado `rejected` (sem redirecionar ao login). */
    onSessionRejected: () => void;
    /**
     * Endereços base das APIs do FitHub (`config`): só uma URL dentro de um deles recebe o token. Em produção as APIs
     * dividem a origem com a web, o Keycloak e os buckets, então a origem sozinha não basta (DEV-0176).
     */
    allowedBases: readonly string[];
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
 * token e tenta mais uma vez. Se a API recusar de novo, marca a sessão como recusada e lança
 * {@link SessionExpiredError}. O token nunca vai para uma URL fora dos endereços de `allowedBases`.
 */
/**
 * A URL fica dentro de um dos endereços base: mesma origem e caminho igual ao da base ou abaixo dele. A URL já vem
 * normalizada pelo `URL` (sem `..`), então `/api/account/../../auth` não passa.
 */
function isInsideAnyBase(target: URL, bases: readonly string[]): boolean {
    return bases.some((base) => {
        const allowed = parseUrl(base);
        if (!allowed || allowed.origin !== target.origin) return false;
        const prefix = allowed.pathname.replace(/\/$/, '');
        return target.pathname === prefix || target.pathname.startsWith(`${prefix}/`);
    });
}

export function createApiFetch(options: ApiFetchOptions): ApiFetch {
    const send = options.fetch ?? fetch;

    return async (url, init) => {
        // URL que nem é válida também é recusada antes de qualquer token ser pedido.
        const target = parseUrl(url);
        if (!target || !isInsideAnyBase(target, options.allowedBases)) {
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
