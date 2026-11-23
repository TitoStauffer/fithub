import * as SecureStore from 'expo-secure-store';

import type { TokenStore } from './session';

const KEY = 'fithub.refresh-token';

/**
 * Só com o aparelho desbloqueado e sem ir para backup nem para outro aparelho: o refresh token não
 * acompanha uma restauração do iCloud.
 */
const OPTIONS: SecureStore.SecureStoreOptions = {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

/** Refresh token no cofre do sistema (Keychain no iOS, Keystore no Android), nunca em AsyncStorage. */
export const secureTokenStore: TokenStore = {
    async load() {
        return (await SecureStore.getItemAsync(KEY, OPTIONS)) ?? undefined;
    },
    save: (refreshToken) => SecureStore.setItemAsync(KEY, refreshToken, OPTIONS),
    clear: () => SecureStore.deleteItemAsync(KEY, OPTIONS),
};
