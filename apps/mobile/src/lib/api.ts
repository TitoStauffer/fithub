import type {
    ClientServiceComponents,
    NutritionServiceComponents,
    NutritionServicePaths,
    TrainingServiceComponents,
} from '@fithub/api-types';

import { clientApiUrl, nutritionApiUrl, trainingApiUrl } from '../features/auth/authConfig';
import { InvalidSessionError } from '../features/auth/session';
import type { ApiFetch } from './apiFetch';

type Schemas = ClientServiceComponents['schemas'];
type TrainingSchemas = TrainingServiceComponents['schemas'];
type NutritionSchemas = NutritionServiceComponents['schemas'];

/** As refeições de hoje: o plano publicado com os macros e os registros do dia (DEV-0087). */
export type MealPlanToday = NutritionSchemas['MealPlanToday'];
export type MealPlanView = NutritionSchemas['MealPlan'];
export type Meal = NutritionSchemas['Meal'];
export type MealItem = NutritionSchemas['MealItem'];
export type MealLog = NutritionSchemas['MealLog'];
export type NutrientAmounts = NutritionSchemas['NutrientAmounts'];
export type MealLogInput = NutritionSchemas['MealLogInput'];
export type MealPhotoUpload = NutritionSchemas['MealPhotoUpload'];
/** Evolução da dieta do próprio paciente (DEV-0092). */
export type DietProgress = NutritionSchemas['DietProgress'];
/** Evolução do treino do próprio aluno (DEV-0091). */
export type TrainingProgress = TrainingSchemas['TrainingProgress'];
/** Série da evolução corporal do próprio cliente (DEV-0093). */
export type BodySeries = Schemas['BodySeries'];
export type BodySeriesPoint = Schemas['BodySeriesPoint'];

/** Ficha ativa do aluno com o próximo treino (DEV-0065), como o training-service devolve. */
export type ActivePlan = TrainingSchemas['ActivePlan'];
export type ClientWorkoutItem = TrainingSchemas['ClientWorkoutItem'];
export type ClientExercise = TrainingSchemas['ClientExercise'];
export type WorkoutSession = TrainingSchemas['WorkoutSession'];
export type SessionView = TrainingSchemas['SessionView'];
export type SessionItem = TrainingSchemas['SessionItem'];
export type RecordedSet = TrainingSchemas['RecordedSet'];
export type RecordedSetInput = TrainingSchemas['RecordedSetInput'];
export type SessionFeedback = TrainingSchemas['SessionFeedback'];
export type SessionHistoryPage = TrainingSchemas['SessionHistoryPage'];
export type SessionSummary = TrainingSchemas['SessionSummary'];
export type ExerciseRecords = TrainingSchemas['ExerciseRecords'];
export type RecordMark = TrainingSchemas['RecordMark'];
export type ManualRecordInput = TrainingSchemas['ManualRecordInput'];
export type ManualRecord = TrainingSchemas['ManualRecord'];
export type ExercisePage = TrainingSchemas['ExercisePage'];
export type WorkoutPlan = TrainingSchemas['WorkoutPlan'];
export type WorkoutPlanInput = TrainingSchemas['WorkoutPlanInput'];

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

type HttpMethod = 'get' | 'put' | 'post' | 'delete';

/** As rotas do contrato do nutrition-service (OpenAPI gerado) que aceitam o método {@code M}. */
type NutritionPath<M extends HttpMethod> = {
    [P in keyof NutritionServicePaths]: [Exclude<NutritionServicePaths[P][M], undefined>] extends [never]
        ? never
        : P;
}[keyof NutritionServicePaths];

/**
 * URL de uma rota do nutrition-service tirada do contrato gerado (DEV-0090, QA do PR #275): rota que não existe no
 * OpenAPI, ou que não aceita o método, não compila. Os parâmetros `{nome}` vêm de `params`, codificados.
 */
export function nutritionUrl<M extends HttpMethod>(
    baseUrl: string,
    _method: M,
    path: NutritionPath<M>,
    params: Record<string, string> = {},
): string {
    return baseUrl + path.replace(/\{(\w+)\}/g, (_, name: string) => encodeURIComponent(params[name] ?? ''));
}

/** As refeições de hoje do plano publicado e os registros do dia (DEV-0087); `null` sem plano publicado. */
export function fetchMealPlanToday(apiFetch: ApiFetch, baseUrl = nutritionApiUrl()) {
    return read<MealPlanToday>(apiFetch, nutritionUrl(baseUrl, 'get', '/me/meal-plan/today'));
}

/** Registra a refeição de hoje com o id gerado no aparelho: repetir com o mesmo id corrige (DEV-0087). */
export function logMeal(apiFetch: ApiFetch, logId: string, body: MealLogInput, baseUrl = nutritionApiUrl()) {
    return write<MealLog>(apiFetch, nutritionUrl(baseUrl, 'put', '/me/meal-logs/{logId}', { logId }), body);
}

/** URL pré-assinada para enviar a foto do prato (DEV-0088). */
export function requestMealPhotoUpload(
    apiFetch: ApiFetch,
    logId: string,
    contentType: string,
    baseUrl = nutritionApiUrl(),
) {
    return post<MealPhotoUpload>(
        apiFetch,
        nutritionUrl(baseUrl, 'post', '/me/meal-logs/{logId}/photo/upload-url', { logId }),
        { contentType },
    );
}

/** Confirma a foto do prato enviada: a API confere tipo e tamanho e troca a anterior (DEV-0088). */
export function confirmMealPhoto(
    apiFetch: ApiFetch,
    logId: string,
    uploadId: string,
    baseUrl = nutritionApiUrl(),
) {
    return write<MealLog>(apiFetch, nutritionUrl(baseUrl, 'put', '/me/meal-logs/{logId}/photo', { logId }), {
        uploadId,
    });
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

function sessionPath(baseUrl: string, sessionId: string) {
    return `${baseUrl}/me/sessions/${encodeURIComponent(sessionId)}`;
}

/** O treino na versão da ficha da sessão, com o prescrito, o feito e o último treino (DEV-0066). */
export async function fetchWorkoutSession(apiFetch: ApiFetch, sessionId: string, baseUrl = trainingApiUrl()) {
    const session = await read<SessionView>(apiFetch, sessionPath(baseUrl, sessionId));
    if (!session) throw new ApiProblemError(404, 'Treino não encontrado.', {});
    return session;
}

/** Registra ou corrige a série pelo id gerado no aparelho: repetir não duplica (DEV-0066). */
export function recordWorkoutSet(
    apiFetch: ApiFetch,
    sessionId: string,
    setId: string,
    body: RecordedSetInput,
    baseUrl = trainingApiUrl(),
) {
    return write<RecordedSet>(
        apiFetch,
        `${sessionPath(baseUrl, sessionId)}/sets/${encodeURIComponent(setId)}`,
        body,
    );
}

/** Apaga a série, no mesmo prazo da correção (DEV-0066). */
export async function removeWorkoutSet(
    apiFetch: ApiFetch,
    sessionId: string,
    setId: string,
    baseUrl = trainingApiUrl(),
): Promise<void> {
    const response = await send(
        apiFetch,
        `${sessionPath(baseUrl, sessionId)}/sets/${encodeURIComponent(setId)}`,
        {
            method: 'DELETE',
        },
    );
    if (!response.ok) throw await problem(response);
}

/** Comentário e sensação do treino (DEV-0067). */
export function saveSessionFeedback(
    apiFetch: ApiFetch,
    sessionId: string,
    body: SessionFeedback,
    baseUrl = trainingApiUrl(),
) {
    return write<SessionView>(apiFetch, `${sessionPath(baseUrl, sessionId)}/feedback`, body);
}

/** Finaliza o treino: a sequência avança para o próximo (DEV-0065). */
export function finishWorkoutSession(apiFetch: ApiFetch, sessionId: string, baseUrl = trainingApiUrl()) {
    return post<WorkoutSession>(apiFetch, `${sessionPath(baseUrl, sessionId)}/finish`);
}

/** Histórico: os treinos finalizados do aluno, do mais novo para o mais antigo, em páginas de 20 (DEV-0072). */
export async function fetchSessionHistory(
    apiFetch: ApiFetch,
    page: number,
    baseUrl = trainingApiUrl(),
): Promise<SessionHistoryPage> {
    const found = await read<SessionHistoryPage>(apiFetch, `${baseUrl}/me/sessions?page=${page}`);
    return found ?? { items: [], total: 0, page, size: 20 };
}

/** Recordes do aluno por exercício: maior carga e 1RM estimado (DEV-0068). */
export async function fetchMyRecords(
    apiFetch: ApiFetch,
    baseUrl = trainingApiUrl(),
): Promise<ExerciseRecords[]> {
    return (await read<ExerciseRecords[]>(apiFetch, `${baseUrl}/me/records`)) ?? [];
}

/** Lança um recorde manual; a API valida carga, repetições, data e exercício (DEV-0068). */
export function addManualRecord(apiFetch: ApiFetch, body: ManualRecordInput, baseUrl = trainingApiUrl()) {
    return post<ManualRecord>(apiFetch, `${baseUrl}/me/records`, body);
}

/** Busca no catálogo de exercícios pelo nome, para o recorde manual (o aluno só vê o catálogo, DEV-0069). */
export async function searchCatalog(apiFetch: ApiFetch, name: string, baseUrl = trainingApiUrl()) {
    const found = await read<ExercisePage>(
        apiFetch,
        `${baseUrl}/exercises?name=${encodeURIComponent(name)}&size=10`,
    );
    return found?.items ?? [];
}

/** As fichas próprias do aluno, da mais nova para a mais antiga (DEV-0069). */
export async function fetchOwnPlans(apiFetch: ApiFetch, baseUrl = trainingApiUrl()): Promise<WorkoutPlan[]> {
    return (await read<WorkoutPlan[]>(apiFetch, `${baseUrl}/me/own-plans`)) ?? [];
}

export function createOwnPlan(apiFetch: ApiFetch, body: WorkoutPlanInput, baseUrl = trainingApiUrl()) {
    return post<WorkoutPlan>(apiFetch, `${baseUrl}/me/own-plans`, body);
}

/** Edita a ficha própria: o rascunho no lugar; a publicada vira a versão seguinte (DEV-0069). */
export function updateOwnPlan(
    apiFetch: ApiFetch,
    planId: string,
    body: WorkoutPlanInput,
    baseUrl = trainingApiUrl(),
) {
    return write<WorkoutPlan>(apiFetch, `${baseUrl}/me/own-plans/${encodeURIComponent(planId)}`, body);
}

export function publishOwnPlan(apiFetch: ApiFetch, planId: string, baseUrl = trainingApiUrl()) {
    return post<WorkoutPlan>(apiFetch, `${baseUrl}/me/own-plans/${encodeURIComponent(planId)}/publish`);
}

/** Um exercício do catálogo, para mostrar o nome na ficha própria. */
export function fetchCatalogExercise(apiFetch: ApiFetch, exerciseId: string, baseUrl = trainingApiUrl()) {
    return read<{ id?: string; name?: string }>(
        apiFetch,
        `${baseUrl}/exercises/${encodeURIComponent(exerciseId)}`,
    );
}

/** Aderência e evolução de carga do próprio aluno de `from` (aaaa-mm-dd) até hoje (DEV-0091). */
export async function fetchMyTrainingProgress(
    apiFetch: ApiFetch,
    from: string,
    baseUrl = trainingApiUrl(),
): Promise<TrainingProgress> {
    return (
        (await read<TrainingProgress>(
            apiFetch,
            `${baseUrl}/me/progress/training?from=${encodeURIComponent(from)}`,
        )) ?? {}
    );
}

/** Aderência à dieta do próprio paciente de `from` (aaaa-mm-dd) até hoje, em todos os planos (DEV-0092). */
export async function fetchMyDietProgress(
    apiFetch: ApiFetch,
    from: string,
    baseUrl = nutritionApiUrl(),
): Promise<DietProgress> {
    const url = `${nutritionUrl(baseUrl, 'get', '/me/progress/nutrition')}?from=${encodeURIComponent(from)}`;
    return (await read<DietProgress>(apiFetch, url)) ?? {};
}

/** Peso, circunferências e % de gordura por método, de todo o histórico do próprio cliente (DEV-0093). */
export async function fetchMyBodySeries(apiFetch: ApiFetch, baseUrl = clientApiUrl()): Promise<BodySeries> {
    return (await read<BodySeries>(apiFetch, `${baseUrl}/clients/me/assessments/series`)) ?? {};
}
