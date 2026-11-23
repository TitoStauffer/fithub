import * as SecureStore from 'expo-secure-store';

import { secureTokenStore } from './secureTokenStore';

jest.mock('expo-secure-store', () => ({
    __esModule: true,
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'apenas-neste-aparelho-desbloqueado',
    AFTER_FIRST_UNLOCK: 'depois-do-primeiro-desbloqueio',
    getItemAsync: jest.fn(),
    setItemAsync: jest.fn(),
    deleteItemAsync: jest.fn(),
}));

const mocked = jest.mocked(SecureStore);
const OPTIONS = { keychainAccessible: 'apenas-neste-aparelho-desbloqueado' };

describe('secureTokenStore', () => {
    it('guarda o refresh token no cofre do sistema, só neste aparelho e desbloqueado', async () => {
        await secureTokenStore.save('refresh');

        expect(mocked.setItemAsync).toHaveBeenCalledWith('fithub.refresh-token', 'refresh', OPTIONS);
    });

    it('lê do mesmo lugar, e ausência vira undefined', async () => {
        mocked.getItemAsync.mockResolvedValueOnce('refresh').mockResolvedValueOnce(null);

        await expect(secureTokenStore.load()).resolves.toBe('refresh');
        await expect(secureTokenStore.load()).resolves.toBeUndefined();
        expect(mocked.getItemAsync).toHaveBeenCalledWith('fithub.refresh-token', OPTIONS);
    });

    it('apaga a mesma chave', async () => {
        await secureTokenStore.clear();

        expect(mocked.deleteItemAsync).toHaveBeenCalledWith('fithub.refresh-token', OPTIONS);
    });
});
