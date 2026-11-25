import { deleteMyAccount } from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que a tela de exclusão da conta precisa da API; injetável nos testes. */
export interface AccountApi {
    deleteAccount(): Promise<void>;
}

export function accountApiFrom(apiFetch: ApiFetch): AccountApi {
    return { deleteAccount: () => deleteMyAccount(apiFetch) };
}

/** O que o cliente digita para confirmar a exclusão. */
export const CONFIRMATION_WORD = 'EXCLUIR';

/** A confirmação vale com o que foi digitado, sem diferenciar maiúsculas nem espaços nas pontas. */
export function isConfirmed(typed: string): boolean {
    return typed.trim().toUpperCase() === CONFIRMATION_WORD;
}
