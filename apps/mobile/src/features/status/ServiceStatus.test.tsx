import { render, screen, userEvent } from '@testing-library/react-native';

import { fetchClientHealth } from './clientHealth';
import { ServiceStatus } from './ServiceStatus';

describe('ServiceStatus', () => {
    it('mostra que está verificando e depois que o serviço está disponível', async () => {
        await render(<ServiceStatus checkHealth={() => Promise.resolve(true)} />);

        expect(await screen.findByText('Serviço disponível.')).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeOnTheScreen();
    });

    it('avisa quando o serviço está fora e tenta de novo pelo botão', async () => {
        const checkHealth = jest.fn<Promise<boolean>, []>().mockRejectedValueOnce(new Error('rede')).mockResolvedValueOnce(true);
        await render(<ServiceStatus checkHealth={checkHealth} />);

        expect(await screen.findByText('Serviço indisponível no momento.')).toBeOnTheScreen();
        await userEvent.press(screen.getByRole('button', { name: 'Tentar de novo' }));

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
