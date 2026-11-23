import type { ClientServiceComponents } from '@fithub/api-types';

import { problem } from './api';
import type { ApiFetch } from './http';

type Schemas = ClientServiceComponents['schemas'];

export type CreatedInvitation = Schemas['CreatedInvitation'];
export type Invitation = Schemas['Invitation'];
export type ProfessionalClient = Schemas['ProfessionalClient'];
export type ClientProfile = Schemas['ClientProfile'];
export type HealthProfile = Schemas['HealthProfile'];
export type Assessment = Schemas['Assessment'];
export type AssessmentRequest = Schemas['AssessmentRequest'];
export type Photo = Schemas['Photo'];

/**
 * O profissional não tem (mais) vínculo ativo com o cliente: a API responde 404 sem revelar o motivo (BR-02). A
 * tela mostra que o acesso acabou (PRD §14).
 */
export class AccessEndedError extends Error {
    constructor() {
        super('O acesso a este cliente acabou: o vínculo foi encerrado.');
        this.name = 'AccessEndedError';
    }
}

const JSON_HEADERS = { Accept: 'application/json', 'Content-Type': 'application/json' };

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

/** Convites e código pessoal do profissional e leitura dos clientes vinculados (client-service, DEV-0035 a 0040). */
export function clientApi(apiFetch: ApiFetch, baseUrl: string) {
    const url = (path: string) => `${baseUrl}${path}`;
    return {
        async createInvitation(email: string | undefined): Promise<CreatedInvitation> {
            const body = email && email.trim().length > 0 ? { email: email.trim() } : {};
            return json(
                await apiFetch(url('/invitations'), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async listInvitations(): Promise<Invitation[]> {
            return json(await apiFetch(url('/invitations'), { headers: { Accept: 'application/json' } }));
        },
        async cancelInvitation(id: string): Promise<void> {
            const response = await apiFetch(url(`/invitations/${encodeURIComponent(id)}`), {
                method: 'DELETE',
            });
            if (!response.ok) throw await problem(response);
        },
        async inviteCode(): Promise<string> {
            const body = await json<{ code?: string }>(
                await apiFetch(url('/professionals/me/invite-code'), {
                    headers: { Accept: 'application/json' },
                }),
            );
            return body.code ?? '';
        },
        async rotateInviteCode(): Promise<string> {
            const body = await json<{ code?: string }>(
                await apiFetch(url('/professionals/me/invite-code'), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                }),
            );
            return body.code ?? '';
        },
        async listClients(): Promise<ProfessionalClient[]> {
            return json(
                await apiFetch(url('/professionals/me/clients'), { headers: { Accept: 'application/json' } }),
            );
        },
        async clientProfile(clientId: string): Promise<ClientProfile> {
            return readLinked<ClientProfile>(
                url(`/professionals/me/clients/${encodeURIComponent(clientId)}/profile`),
            );
        },
        /** Saúde do cliente vinculado; `null` se ele ainda não informou. */
        async clientHealth(clientId: string): Promise<HealthProfile | null> {
            const response = await apiFetch(
                url(`/professionals/me/clients/${encodeURIComponent(clientId)}/health`),
                { headers: { Accept: 'application/json' } },
            );
            if (response.status === 404) {
                const error = await problem(response);
                // 404 sem vínculo (acesso acabou) ou sem saúde informada: a API diz qual no detail.
                if (error.message.startsWith('O cliente ainda não informou')) return null;
                throw new AccessEndedError();
            }
            return json(response);
        },
        /** Histórico de avaliações do cliente vinculado (DEV-0044). */
        async clientAssessments(clientId: string): Promise<Assessment[]> {
            return readLinked<Assessment[]>(
                url(`/professionals/me/clients/${encodeURIComponent(clientId)}/assessments`),
            );
        },
        /** Fotos da avaliação do cliente vinculado, com URL de leitura de 5 minutos (DEV-0048). */
        async clientAssessmentPhotos(clientId: string, assessmentId: string): Promise<Photo[]> {
            return readLinked<Photo[]>(
                url(
                    `/professionals/me/clients/${encodeURIComponent(clientId)}/assessments/${encodeURIComponent(assessmentId)}/photos`,
                ),
            );
        },
        /** Registra a avaliação presencial do cliente vinculado (DEV-0044 a 0047). */
        async recordPresential(clientId: string, body: AssessmentRequest): Promise<Assessment> {
            const response = await apiFetch(
                url(`/professionals/me/clients/${encodeURIComponent(clientId)}/assessments`),
                { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) },
            );
            if (response.status === 404) throw new AccessEndedError();
            return json(response);
        },
        async endLink(linkId: string): Promise<void> {
            const response = await apiFetch(url(`/links/${encodeURIComponent(linkId)}/end`), {
                method: 'POST',
                headers: { Accept: 'application/json' },
            });
            if (!response.ok) throw await problem(response);
        },
    };

    async function readLinked<T>(target: string): Promise<T> {
        const response = await apiFetch(target, { headers: { Accept: 'application/json' } });
        if (response.status === 404) throw new AccessEndedError();
        return json(response);
    }
}

export type ClientApi = ReturnType<typeof clientApi>;
