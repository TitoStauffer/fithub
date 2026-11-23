import {
    ApiProblemError,
    OfflineError,
    fetchMyHealth,
    fetchMyProfile,
    saveMyHealth,
    saveMyProfile,
    type ClientProfile,
    type ClientProfileRequest,
    type HealthProfile,
    type HealthProfileRequest,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que as telas de perfil precisam da API; injetável nos testes. */
export interface ProfileApi {
    fetchProfile(): Promise<ClientProfile | null>;
    saveProfile(body: ClientProfileRequest): Promise<ClientProfile>;
    fetchHealth(): Promise<HealthProfile | null>;
    saveHealth(body: HealthProfileRequest): Promise<HealthProfile>;
}

export function profileApiFrom(apiFetch: ApiFetch): ProfileApi {
    return {
        fetchProfile: () => fetchMyProfile(apiFetch),
        saveProfile: (body) => saveMyProfile(apiFetch, body),
        fetchHealth: () => fetchMyHealth(apiFetch),
        saveHealth: (body) => saveMyHealth(apiFetch, body),
    };
}

/** Mensagem para a pessoa: sem conexão, a mensagem da API ou uma genérica. */
export function messageOf(error: unknown): string {
    if (error instanceof OfflineError) return error.message;
    if (error instanceof ApiProblemError) return error.message;
    return 'Não foi possível concluir. Tente de novo.';
}

/**
 * Separa o erro da API entre os campos que a tela mostra e o aviso geral: um campo que o formulário não exibe
 * vai para o aviso, senão a pessoa não sabe o que corrigir (aprendizado do PR #202).
 */
export function splitErrors<K extends string>(
    error: unknown,
    fields: readonly K[],
): { fieldErrors: Partial<Record<K, string>>; formError?: string } {
    if (!(error instanceof ApiProblemError) || Object.keys(error.fieldErrors).length === 0) {
        return { fieldErrors: {}, formError: messageOf(error) };
    }
    const fieldErrors: Partial<Record<K, string>> = {};
    const hidden: string[] = [];
    for (const [field, message] of Object.entries(error.fieldErrors)) {
        if ((fields as readonly string[]).includes(field)) fieldErrors[field as K] = message;
        else hidden.push(message);
    }
    return { fieldErrors, formError: hidden.length > 0 ? hidden.join(' ') : undefined };
}
