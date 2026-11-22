import { act, render, screen, userEvent } from '@testing-library/react-native';

import { fetchClientHealth } from './clientHealth';
import { ServiceStatus } from './ServiceStatus';

/** Promessa resolvida pelo teste, para ver a tela enquanto a resposta não chega. */
function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, resolve, reject };
}

describe('ServiceStatus', () => {
    it('mostra que está verificando e depois que o serviço está disponível', async () => {
        const response = deferred<boolean>();
        await render(<ServiceStatus checkHealth={() => response.promise} />);

        expect(screen.getByText('Verificando o serviço…')).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeOnTheScreen();

        await act(async () => response.resolve(true));

        expect(await screen.findByText('Serviço disponível.')).toBeOnTheScreen();
        expect(screen.queryByText('Verificando o serviço…')).not.toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeOnTheScreen();
    });

    it('avisa quando o serviço está fora, volta a verificar e tenta de novo pelo botão', async () => {
        const firstTry = deferred<boolean>();
        const secondTry = deferred<boolean>();
        const checkHealth = jest
            .fn<Promise<boolean>, []>()
            .mockReturnValueOnce(firstTry.promise)
            .mockReturnValueOnce(secondTry.promise);
        await render(<ServiceStatus checkHealth={checkHealth} />);

        await act(async () => firstTry.reject(new Error('rede')));
        expect(await screen.findByText('Serviço indisponível no momento.')).toBeOnTheScreen();

        await userEvent.press(screen.getByRole('button', { name: 'Tentar de novo' }));
        // Enquanto a nova tentativa não responde, a tela volta a "verificando" e o botão some.
        expect(await screen.findByText('Verificando o serviço…')).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeOnTheScreen();

        await act(async () => secondTry.resolve(true));
        expect(await screen.findByText('Serviço disponível.')).toBeOnTheScreen();
        expect(checkHealth).toHaveBeenCalledTimes(2);
    });
});

describe('fetchClientHealth', () => {
    const originalFetch = global.fetch;

    afterEach(() => {
        global.fetch = originalFetch;
    });

    it('só considera disponível quando o health responde UP', async () => {
        const fetchMock = jest.fn<Promise<Response>, Parameters<typeof fetch>>();
        global.fetch = fetchMock;
        const reply = (body: object, ok = true) => ({ ok, json: () => Promise.resolve(body) }) as Response;

        fetchMock.mockResolvedValueOnce(reply({ status: 'UP' }));
        await expect(fetchClientHealth('http://api.test')).resolves.toBe(true);
        expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/actuator/health');

        fetchMock.mockResolvedValueOnce(reply({ status: 'DOWN' }, false));
        await expect(fetchClientHealth('http://api.test')).resolves.toBe(false);

        fetchMock.mockResolvedValueOnce(reply({ status: 'OUT_OF_SERVICE' }));
        await expect(fetchClientHealth('http://api.test')).resolves.toBe(false);
    });
});
