import type { ClientServiceComponents, TrainingServiceComponents } from '@fithub/api-types';

import { clientApiUrl, trainingApiUrl } from '../features/auth/authConfig';
import { InvalidSessionError } from '../features/auth/session';
import type { ApiFetch } from './apiFetch';

type Schemas = ClientServiceComponents['schemas'];
type TrainingSchemas = TrainingServiceComponents['schemas'];

/** Ficha ativa do aluno com o próximo treino (DEV-0065), como o training-service devolve. */
export type ActivePlan = TrainingSchemas['ActivePlan'];
export type ClientWorkoutItem = TrainingSchemas['ClientWorkoutItem'];
export type ClientExercise = TrainingSchemas['ClientExercise'];
export type WorkoutSession = TrainingSchemas['WorkoutSession'];

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

export type InvitationPreview = Schemas['InvitationPreview'];
export type AcceptInvitationRequest = Schemas['AcceptInvitationRequest'];
export type AcceptedLink = Schemas['Link'];
export type ClientLink = Schemas['ClientLink'];
export type LinkStatus = Schemas['LinkStatus'];

async function post<T>(apiFetch: ApiFetch, url: string, body?: unknown): Promise<T> {
    const response = await send(apiFetch, url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

/** Consulta o convite pelo token do link ou pelo código (DEV-0035); o segredo vai no corpo, nunca na URL. */
export function lookupInvitation(
    apiFetch: ApiFetch,
    target: { token: string } | { code: string },
    baseUrl = clientApiUrl(),
) {
    return post<InvitationPreview>(apiFetch, `${baseUrl}/invitations/lookup`, target);
}

/** Aceita o convite com o consentimento explícito e a versão do termo mostrada (DEV-0036). */
export function acceptInvitation(
    apiFetch: ApiFetch,
    body: AcceptInvitationRequest,
    baseUrl = clientApiUrl(),
) {
    return post<AcceptedLink>(apiFetch, `${baseUrl}/links`, body);
}

/** Vínculos do cliente, ativos e encerrados (DEV-0037). */
export async function fetchMyLinks(apiFetch: ApiFetch, baseUrl = clientApiUrl()): Promise<ClientLink[]> {
    return (await read<ClientLink[]>(apiFetch, `${baseUrl}/links`)) ?? [];
}

/** Encerra o vínculo; encerrar de novo devolve o primeiro encerramento (DEV-0037). */
export function endLink(apiFetch: ApiFetch, linkId: string, baseUrl = clientApiUrl()) {
    return post<LinkStatus>(apiFetch, `${baseUrl}/links/${encodeURIComponent(linkId)}/end`);
}

export type Assessment = Schemas['Assessment'];
export type AssessmentRequest = Schemas['AssessmentRequest'];
export type Photo = Schemas['Photo'];
export type PhotoUpload = Schemas['PhotoUpload'];
export type PhotoPose = NonNullable<Photo['pose']>;

/** Avaliação online do próprio cliente (DEV-0044); a API calcula a estimativa da Marinha quando dá (DEV-0046). */
export function recordOnlineAssessment(
    apiFetch: ApiFetch,
    body: AssessmentRequest,
    baseUrl = clientApiUrl(),
) {
    return post<Assessment>(apiFetch, `${baseUrl}/clients/me/assessments`, body);
}

function photoPath(baseUrl: string, assessmentId: string, pose: PhotoPose) {
    return `${baseUrl}/clients/me/assessments/${encodeURIComponent(assessmentId)}/photos/${pose}`;
}

/** Pede a URL pré-assinada de envio da foto da pose (DEV-0048). */
export function requestPhotoUpload(
    apiFetch: ApiFetch,
    assessmentId: string,
    pose: PhotoPose,
    contentType: string,
    baseUrl = clientApiUrl(),
) {
    return post<PhotoUpload>(apiFetch, `${photoPath(baseUrl, assessmentId, pose)}/upload-url`, {
        contentType,
    });
}

/** Confirma o envio: a API confere tipo e tamanho no objeto e troca a foto anterior da pose (DEV-0048). */
export function confirmPhoto(
    apiFetch: ApiFetch,
    assessmentId: string,
    pose: PhotoPose,
    uploadId: string,
    baseUrl = clientApiUrl(),
) {
    return post<Photo>(apiFetch, `${photoPath(baseUrl, assessmentId, pose)}/confirm`, { uploadId });
}

/**
 * Envia o arquivo para a URL pré-assinada. Vai direto ao armazenamento, sem a sessão: a URL já carrega a
 * autorização e o token do FitHub nunca sai para outro endereço.
 */
export async function putToPresignedUrl(
    uploadUrl: string,
    file: Blob,
    contentType: string,
    fetchImpl: typeof fetch = fetch,
): Promise<void> {
    let response: Response;
    try {
        response = await fetchImpl(uploadUrl, {
            method: 'PUT',
            headers: { 'Content-Type': contentType },
            body: file,
        });
    } catch (error) {
        if (error instanceof TypeError) throw new OfflineError();
        throw error;
    }
    if (!response.ok) {
        throw new ApiProblemError(response.status, 'Não foi possível enviar a foto. Tente de novo.', {});
    }
}

/** Histórico de avaliações do próprio cliente, da mais recente para a mais antiga (DEV-0044). */
export async function fetchMyAssessments(
    apiFetch: ApiFetch,
    baseUrl = clientApiUrl(),
): Promise<Assessment[]> {
    return (await read<Assessment[]>(apiFetch, `${baseUrl}/clients/me/assessments`)) ?? [];
}

/** Fotos da avaliação com URL de leitura de 5 minutos, pedida na hora de mostrar (DEV-0048, DEV-0051). */
export async function fetchMyPhotos(
    apiFetch: ApiFetch,
    assessmentId: string,
    baseUrl = clientApiUrl(),
): Promise<Photo[]> {
    return (
        (await read<Photo[]>(
            apiFetch,
            `${baseUrl}/clients/me/assessments/${encodeURIComponent(assessmentId)}/photos`,
        )) ?? []
    );
}

/** As fichas publicadas do aluno, cada uma com o próximo treino da sequência (DEV-0065). */
export async function fetchActivePlans(
    apiFetch: ApiFetch,
    baseUrl = trainingApiUrl(),
): Promise<ActivePlan[]> {
    return (await read<ActivePlan[]>(apiFetch, `${baseUrl}/me/plans/active`)) ?? [];
}

/** Inicia o próximo treino da ficha com o id gerado no aparelho: repetir com o mesmo id não duplica (DEV-0065). */
export function startWorkoutSession(
    apiFetch: ApiFetch,
    sessionId: string,
    planId: string,
    baseUrl = trainingApiUrl(),
) {
    return write<WorkoutSession>(apiFetch, `${baseUrl}/me/sessions/${encodeURIComponent(sessionId)}`, {
        planId,
    });
}
