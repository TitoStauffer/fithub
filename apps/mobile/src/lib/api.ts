import type { ClientServiceComponents } from '@fithub/api-types';

import { clientApiUrl } from '../features/auth/authConfig';
import { InvalidSessionError } from '../features/auth/session';
import type { ApiFetch } from './apiFetch';

type Schemas = ClientServiceComponents['schemas'];

/** Perfil do cliente como a API devolve (com a idade calculada). */
export type ClientProfile = Schemas['ClientProfile'];
export type ClientProfileRequest = Schemas['ClientProfileRequest'];
export type HealthProfile = Schemas['HealthProfile'];
export type HealthProfileRequest = Schemas['HealthProfileRequest'];

/** Erro da API em Problem Details: `detail` e, quando houver, a mensagem de cada campo. */
export class ApiProblemError extends Error {
    readonly status: number;
    readonly fieldErrors: Record<string, string>;

    constructor(status: number, detail: string, fieldErrors: Record<string, string>) {
        super(detail);
        this.name = 'ApiProblemError';
        this.status = status;
        this.fieldErrors = fieldErrors;
    }
}

/** Sem conexão com a API: a tela avisa e mantém o que foi digitado (PRD §14). */
export class OfflineError extends Error {
    constructor() {
        super('Sem conexão com o FitHub. Confira a internet e tente de novo.');
        this.name = 'OfflineError';
    }
}

async function send(apiFetch: ApiFetch, url: string, init?: RequestInit): Promise<Response> {
    try {
        return await apiFetch(url, init);
    } catch (error) {
        // Sessão recusada continua sendo sessão recusada; falha de rede vira "sem conexão".
        if (error instanceof InvalidSessionError) throw error;
        if (error instanceof TypeError) throw new OfflineError();
        throw error;
    }
}

async function problem(response: Response): Promise<ApiProblemError> {
    const body = (await response.json().catch(() => ({}))) as { detail?: unknown; errors?: unknown };
    const fieldErrors: Record<string, string> = {};
    if (Array.isArray(body.errors)) {
        for (const item of body.errors as { field?: unknown; message?: unknown }[]) {
            if (typeof item.field === 'string' && typeof item.message === 'string') {
                fieldErrors[item.field] = item.message;
            }
        }
    }
    const detail =
        typeof body.detail === 'string' ? body.detail : 'Não foi possível concluir. Tente de novo.';
    return new ApiProblemError(response.status, detail, fieldErrors);
}

async function read<T>(apiFetch: ApiFetch, url: string): Promise<T | null> {
    const response = await send(apiFetch, url);
    if (response.status === 404) return null;
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

async function write<T>(apiFetch: ApiFetch, url: string, body: unknown): Promise<T> {
    const response = await send(apiFetch, url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

/** Perfil do cliente autenticado (DEV-0032); `null` se ainda não houver. */
export function fetchMyProfile(apiFetch: ApiFetch, baseUrl = clientApiUrl()) {
    return read<ClientProfile>(apiFetch, `${baseUrl}/clients/me/profile`);
}

export function saveMyProfile(apiFetch: ApiFetch, body: ClientProfileRequest, baseUrl = clientApiUrl()) {
    return write<ClientProfile>(apiFetch, `${baseUrl}/clients/me/profile`, body);
}

/** Perfil de saúde do cliente autenticado (DEV-0033); `null` se ainda não houver. Nunca é guardado no aparelho. */
export function fetchMyHealth(apiFetch: ApiFetch, baseUrl = clientApiUrl()) {
    return read<HealthProfile>(apiFetch, `${baseUrl}/clients/me/health`);
}

export function saveMyHealth(apiFetch: ApiFetch, body: HealthProfileRequest, baseUrl = clientApiUrl()) {
    return write<HealthProfile>(apiFetch, `${baseUrl}/clients/me/health`, body);
}
