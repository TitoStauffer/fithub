// @vitest-environment node
import { createServer as createHttpServer, type IncomingMessage, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { fileURLToPath } from 'node:url';
import { createServer, loadEnv, type ViteDevServer } from 'vite';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { devApiProxy } from './devProxy';

/** Serviço falso: responde com o caminho e o Host que recebeu. */
function fakeService(name: string): Promise<Server> {
    const server = createHttpServer((req: IncomingMessage, res) => {
        res.setHeader('content-type', 'application/json');
        res.end(JSON.stringify({ service: name, path: req.url, host: req.headers.host }));
    });
    return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

const port = (server: Server) => (server.address() as AddressInfo).port;

describe('proxy do servidor de desenvolvimento (DEV-0156)', () => {
    const services: Record<string, Server> = {};
    let vite: ViteDevServer;
    let base: string;

    beforeAll(async () => {
        for (const name of ['account', 'client', 'training', 'nutrition'])
            services[name] = await fakeService(name);
        vite = await createServer({
            configFile: false,
            logLevel: 'silent',
            server: {
                port: 0,
                host: '127.0.0.1',
                proxy: devApiProxy({
                    account: port(services.account!),
                    client: port(services.client!),
                    training: port(services.training!),
                    nutrition: port(services.nutrition!),
                }),
            },
        });
        await vite.listen();
        base = `http://127.0.0.1:${(vite.httpServer!.address() as AddressInfo).port}`;
    });

    afterAll(async () => {
        await vite?.close();
        for (const server of Object.values(services)) server.close();
    });

    it.each(['account', 'client', 'training', 'nutrition'])(
        'leva /api/%s ao serviço, sem o prefixo e com o Host da página',
        async (service) => {
            const response = await fetch(`${base}/api/${service}/professionals/me?x=1`);

            expect(await response.json()).toEqual({
                service,
                path: '/professionals/me?x=1',
                host: new URL(base).host,
            });
        },
    );

    it('leva o prefixo sozinho à raiz do serviço', async () => {
        const response = await fetch(`${base}/api/client`);

        expect(await response.json()).toMatchObject({ service: 'client', path: '/' });
    });

    it('não leva ao serviço um caminho que só começa com o mesmo nome', async () => {
        const response = await fetch(`${base}/api/accounts/x`);

        expect(response.headers.get('content-type') ?? '').not.toContain('application/json');
    });
});

describe('configuração do pnpm dev (DEV-0156)', () => {
    it('o vite.config usa o proxy das quatro APIs', async () => {
        const { default: config } = await import('../vite.config');

        expect(Object.keys(config.server?.proxy ?? {})).toEqual(Object.keys(devApiProxy()));
    });

    it('o .env.development aponta cada API para o caminho do proxy', () => {
        const env = loadEnv('development', fileURLToPath(new URL('..', import.meta.url)), 'VITE_');

        expect(env).toMatchObject({
            VITE_ACCOUNT_API_URL: '/api/account',
            VITE_CLIENT_API_URL: '/api/client',
            VITE_TRAINING_API_URL: '/api/training',
            VITE_NUTRITION_API_URL: '/api/nutrition',
        });
    });
});
