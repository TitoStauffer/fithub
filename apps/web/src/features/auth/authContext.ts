import { createContext, useContext } from 'react';

import type { AuthUser } from './authClient';

/**
 * - `loading`: ainda descobrindo se há sessão.
 * - `authenticated`: há sessão e `user` está preenchido.
 * - `anonymous`: sem sessão (nunca entrou, saiu ou a sessão venceu).
 * - `failed`: o provedor de identidade não respondeu ou devolveu erro.
 * - `rejected`: a API do FitHub recusou até o token renovado. Não há redirecionamento automático: a
 *   pessoa sai (encerrando a sessão no Keycloak) e entra de novo, o que quebra o laço de login.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'failed' | 'rejected';

/** A sessão venceu ou foi encerrada: a chamada à API não foi feita ou não pode ser repetida. */
export class SessionExpiredError extends Error {
    constructor() {
        super('Sessão encerrada. Entre de novo.');
        this.name = 'SessionExpiredError';
    }
}

/** Sem conexão com o Keycloak e sem token válido: a sessão continua, mas a chamada não pode ser feita agora. */
export class SessionUnavailableError extends Error {
    constructor() {
        super('Sem conexão com o FitHub. Tente de novo em instantes.');
        this.name = 'SessionUnavailableError';
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
     * {@link SessionExpiredError}. Falha passageira com token ainda válido devolve o token atual; sem
     * token válido, lança {@link SessionUnavailableError} e mantém a sessão.
     */
    getAccessToken: (options?: { force?: boolean }) => Promise<string>;
    /** Encerra a sessão local (o Keycloak recusou a renovação). */
    endSession: () => void;
    /** A API recusou o token mesmo depois de renovado: apaga os tokens e mostra o erro, sem redirecionar. */
    rejectSession: () => void;
    /**
     * Pede um token novo ao Keycloak e atualiza o usuário (papéis inclusos). Usado depois do cadastro do
     * profissional, para o token trazer o papel novo sem sair e entrar de novo (DEV-0031).
     */
    reloadSession: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | undefined>(undefined);

export function useAuth(): AuthState {
    const state = useContext(AuthContext);
    if (!state) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
    return state;
}
