import { render, screen, userEvent } from '@testing-library/react-native';

import App from './App';
import { fakeGateway, fakeStore } from './src/features/auth/fakes';
import { createSession } from './src/features/auth/session';

// O status do serviço não é assunto destes testes; os módulos nativos do Expo não rodam no Jest.
jest.mock('./src/features/status/ServiceStatus', () => ({ ServiceStatus: () => null }));
// A área do cliente tem testes próprios (ClientArea.test.tsx).
jest.mock('./src/features/profile/ClientArea', () => ({ ClientArea: () => null }));
jest.mock('expo-auth-session', () => ({ __esModule: true }));
jest.mock('expo-secure-store', () => ({ __esModule: true }));

function setup(storedRefreshToken?: string) {
    const gateway = fakeGateway();
    const store = fakeStore(storedRefreshToken);
    return { gateway, store, session: createSession(gateway, store) };
}

describe('App', () => {
    it('sem sessão, mostra a tela de entrada', async () => {
        const { session } = setup();
        await render(<App session={session} />);

        expect(await screen.findByRole('button', { name: 'Entrar' })).toBeOnTheScreen();
        expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeOnTheScreen();
    });

    it('entrar leva à área do cliente e guarda o refresh token', async () => {
        const { session, store } = setup();
        await render(<App session={session} />);

        await userEvent.press(await screen.findByRole('button', { name: 'Entrar' }));

        expect(await screen.findByRole('button', { name: 'Sair' })).toBeOnTheScreen();
        expect(store.value).toBe('refresh-login');
    });

    it('ao reabrir o app com sessão guardada, vai direto para a área do cliente', async () => {
        const { session, gateway } = setup('refresh-guardado');
        await render(<App session={session} />);

        expect(await screen.findByRole('button', { name: 'Sair' })).toBeOnTheScreen();
        expect(gateway.calls).toEqual(['refresh:refresh-guardado']);
    });

    it('sessão guardada inválida leva à tela de entrada', async () => {
        const { session, gateway, store } = setup('refresh-revogado');
        gateway.refreshMode = 'invalid';
        await render(<App session={session} />);

        expect(await screen.findByRole('button', { name: 'Entrar' })).toBeOnTheScreen();
        expect(store.value).toBeUndefined();
    });

    it('sem conexão ao abrir, avisa e tenta de novo pelo botão, sem perder a sessão guardada', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        gateway.refreshMode = 'offline';
        await render(<App session={session} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/Não foi possível falar com o FitHub/);
        expect(store.value).toBe('refresh-guardado');

        gateway.refreshMode = 'ok';
        await userEvent.press(screen.getByRole('button', { name: 'Tentar de novo' }));
        expect(await screen.findByRole('button', { name: 'Sair' })).toBeOnTheScreen();
    });

    it('falha no login mostra o erro e deixa tentar de novo', async () => {
        const { session, gateway } = setup();
        gateway.signInResult = new Error('O login não foi concluído.');
        await render(<App session={session} />);

        await userEvent.press(await screen.findByRole('button', { name: 'Entrar' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(/Não foi possível entrar/);
        expect(screen.getByRole('button', { name: 'Entrar' })).toBeOnTheScreen();
    });

    it('sair volta para a tela de entrada e apaga o que foi guardado', async () => {
        const { session, gateway, store } = setup('refresh-guardado');
        await render(<App session={session} />);

        await userEvent.press(await screen.findByRole('button', { name: 'Sair' }));

        expect(await screen.findByRole('button', { name: 'Entrar' })).toBeOnTheScreen();
        expect(store.value).toBeUndefined();
        expect(gateway.calls).toContain('signOut:refresh-renewed-1');
    });
});
