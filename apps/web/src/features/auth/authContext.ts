import { createContext, useContext } from 'react';

import type { AuthUser } from './authClient';

/**
 * - `loading`: ainda descobrindo se há sessão.
 * - `authenticated`: há sessão e `user` está preenchido.
 * - `anonymous`: sem sessão (nunca entrou, saiu ou a sessão venceu).
 * - `failed`: o provedor de identidade não respondeu ou devolveu erro.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'failed';

/** A sessão venceu ou foi encerrada: a chamada à API não foi feita ou não pode ser repetida. */
export class SessionExpiredError extends Error {
    constructor() {
        super('Sessão encerrada. Entre de novo.');
        this.name = 'SessionExpiredError';
    }
}

export interface AuthState {
    status: AuthStatus;
    user: AuthUser | undefined;
    /** Leva ao login e volta para a página atual. */
    login: () => Promise<void>;
    /** Encerra a sessão no Keycloak e volta para a página inicial. */
    logout: () => Promise<void>;
    /**
     * Token de acesso válido para o cabeçalho Authorization. Renova antes se estiver para vencer;
     * `force` renova sempre (depois de um 401). Sem sessão, encerra a sessão local e lança
     * {@link SessionExpiredError}.
     */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** Encerra a sessão local (a API recusou o token mesmo depois de renovado). */
    endSession: () => void;
}

export const AuthContext = createContext<AuthState | undefined>(undefined);

export function useAuth(): AuthState {
    const state = useContext(AuthContext);
    if (!state) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
    return state;
}
