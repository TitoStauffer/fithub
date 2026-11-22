import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchAccountHealth } from './accountHealth';
import { ServiceStatus } from './ServiceStatus';

function renderWithQuery(ui: ReactNode) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('ServiceStatus', () => {
    it('mostra que está verificando e depois que o serviço está disponível', async () => {
        renderWithQuery(<ServiceStatus checkHealth={() => Promise.resolve(true)} />);

        expect(screen.getByRole('status')).toHaveTextContent('Verificando o serviço…');
        expect(await screen.findByText('Serviço disponível.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument();
    });

    it('avisa quando o serviço está fora e tenta de novo pelo botão', async () => {
        const checkHealth = vi
            .fn<() => Promise<boolean>>()
            .mockRejectedValueOnce(new Error('rede'))
            .mockResolvedValueOnce(true);
        renderWithQuery(<ServiceStatus checkHealth={checkHealth} />);

        expect(await screen.findByText('Serviço indisponível no momento.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

        expect(await screen.findByText('Serviço disponível.')).toBeInTheDocument();
        expect(checkHealth).toHaveBeenCalledTimes(2);
    });
});

describe('fetchAccountHealth', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('só considera disponível quando o health responde UP', async () => {
        const fetchMock = vi.fn<typeof fetch>();
        vi.stubGlobal('fetch', fetchMock);

        fetchMock.mockResolvedValueOnce(Response.json({ status: 'UP' }));
        await expect(fetchAccountHealth('http://api.test')).resolves.toBe(true);
        expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/actuator/health');

        fetchMock.mockResolvedValueOnce(Response.json({ status: 'DOWN' }, { status: 503 }));
        await expect(fetchAccountHealth('http://api.test')).resolves.toBe(false);

        fetchMock.mockResolvedValueOnce(Response.json({ status: 'OUT_OF_SERVICE' }));
        await expect(fetchAccountHealth('http://api.test')).resolves.toBe(false);
    });
});
