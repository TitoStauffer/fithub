import type { ProxyOptions } from 'vite';

/** Porta de cada serviço no ambiente local (infra/README.md). */
export const DEV_API_PORTS = { account: 8081, client: 8082, training: 8083, nutrition: 8084 } as const;

export type DevApiPorts = Record<keyof typeof DEV_API_PORTS, number>;

/**
 * Proxy do servidor de desenvolvimento (DEV-0156): `/api/<serviço>` vai ao serviço local sem o prefixo, como o
 * Traefik faz em produção (ADR-014). A web e as APIs ficam na mesma origem, e nenhum serviço precisa de CORS.
 *
 * O `Host` original é mantido (sem `changeOrigin`): com ele igual à origem da página, o Spring não trata a chamada
 * como CORS. A chave é uma expressão regular, para `/api/accounts` não cair no account-service.
 */
export function devApiProxy(ports: DevApiPorts = DEV_API_PORTS): Record<string, ProxyOptions> {
    return Object.fromEntries(
        Object.entries(ports).map(([service, port]) => {
            const prefix = `/api/${service}`;
            return [
                `^${prefix}(/|$)`,
                {
                    target: `http://127.0.0.1:${port}`,
                    // O caminho vazio (o prefixo sozinho) chega ao serviço como "/".
                    rewrite: (path: string) => path.slice(prefix.length),
                },
            ];
        }),
    );
}
