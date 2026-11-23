import type { AccountServiceComponents, AccountServicePaths } from '@fithub/api-types';

import type { ApiFetch } from './http';

/** Rotas do account-service, tipadas a partir do OpenAPI (packages/api-types, DEV-0022). */
export type AccountApi = AccountServicePaths;
export type Professional = AccountServiceComponents['schemas']['Professional'];
export type ProfessionalRegistrationRequest =
    AccountServiceComponents['schemas']['ProfessionalRegistrationRequest'];

/** Erro da API em Problem Details (RFC 9457), com os campos inválidos quando houver. */
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

async function problem(response: Response): Promise<ApiProblemError> {
    const body = (await response.json().catch(() => ({}))) as {
        detail?: unknown;
        errors?: unknown;
    };
    const fieldErrors: Record<string, string> = {};
    if (Array.isArray(body.errors)) {
        for (const item of body.errors as { field?: unknown; message?: unknown }[]) {
            if (typeof item.field === 'string' && typeof item.message === 'string')
                fieldErrors[item.field] = item.message;
        }
    }
    const detail =
        typeof body.detail === 'string' ? body.detail : 'Não foi possível concluir. Tente de novo.';
    return new ApiProblemError(response.status, detail, fieldErrors);
}

/** Cadastro de profissional da conta autenticada (DEV-0029); `null` quando ainda não há (o TanStack Query não aceita `undefined`). */
export async function fetchMyProfessional(apiFetch: ApiFetch, baseUrl: string): Promise<Professional | null> {
    const response = await apiFetch(`${baseUrl}/professionals/me`, {
        headers: { Accept: 'application/json' },
    });
    if (response.status === 404) return null;
    if (!response.ok) throw await problem(response);
    return (await response.json()) as Professional;
}

/** Cadastra a conta autenticada como profissional. 201 cria; 200 se o mesmo cadastro já existia. */
export async function registerProfessional(
    apiFetch: ApiFetch,
    baseUrl: string,
    request: ProfessionalRegistrationRequest,
): Promise<Professional> {
    const response = await apiFetch(`${baseUrl}/professionals/me`, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    });
    if (!response.ok) throw await problem(response);
    return (await response.json()) as Professional;
}
