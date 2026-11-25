import type { TrainingServiceComponents } from '@fithub/api-types';

import { problem } from './api';
import { AccessEndedError } from './clientApi';
import type { ApiFetch } from './http';

type Schemas = TrainingServiceComponents['schemas'];

export type Exercise = Schemas['Exercise'];
export type ExercisePage = Schemas['ExercisePage'];
export type ExerciseInput = Schemas['ExerciseInput'];
// O OpenAPI do springdoc marca todo campo de resposta como opcional; a tela trata a ausência.
export type MuscleGroup = NonNullable<Exercise['muscleGroups']>[number];
export type Equipment = NonNullable<Exercise['equipment']>;
export type WorkoutPlan = Schemas['WorkoutPlan'];
export type WorkoutPlanInput = Schemas['WorkoutPlanInput'];
export type WorkoutItemInput = Schemas['WorkoutItemInput'];
export type WorkoutPlanAlert = Schemas['WorkoutPlanAlert'];
export type Technique = NonNullable<WorkoutItemInput['technique']>;
export type BodyRegion = NonNullable<WorkoutPlanAlert['region']>;
export type MobilityRestriction = Schemas['MobilityRestriction'];
export type MobilityRestrictionInput = Schemas['MobilityRestrictionInput'];
export type ExpiringPlan = Schemas['ExpiringPlan'];
export type TrainingProgress = Schemas['TrainingProgress'];
export type ExerciseRecords = Schemas['ExerciseRecords'];

/** Filtros da busca (DEV-0055); campo vazio não filtra. */
export interface ExerciseSearch {
    name?: string;
    muscleGroup?: MuscleGroup | '';
    equipment?: Equipment | '';
    page?: number;
    size?: number;
}

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

const JSON_HEADERS = { Accept: 'application/json', 'Content-Type': 'application/json' };

/** Catálogo de exercícios e exercícios próprios do personal (training-service, DEV-0053 a DEV-0056). */
export function trainingApi(apiFetch: ApiFetch, baseUrl: string) {
    const url = (path: string) => `${baseUrl}${path}`;
    const exerciseUrl = (id: string) => url(`/exercises/${encodeURIComponent(id)}`);
    const planUrl = (id: string) => url(`/plans/${encodeURIComponent(id)}`);
    const restrictionsUrl = (clientId: string) =>
        url(`/clients/${encodeURIComponent(clientId)}/restrictions`);
    return {
        async searchExercises(search: ExerciseSearch): Promise<ExercisePage> {
            const params = new URLSearchParams();
            if (search.name && search.name.trim().length > 0) params.set('name', search.name.trim());
            if (search.muscleGroup) params.set('muscleGroup', search.muscleGroup);
            if (search.equipment) params.set('equipment', search.equipment);
            if (search.page !== undefined) params.set('page', String(search.page));
            if (search.size !== undefined) params.set('size', String(search.size));
            const query = params.toString();
            return json(
                await apiFetch(url(`/exercises${query ? `?${query}` : ''}`), {
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        async exercise(id: string): Promise<Exercise> {
            return json(await apiFetch(exerciseUrl(id), { headers: { Accept: 'application/json' } }));
        },
        async createExercise(input: ExerciseInput): Promise<Exercise> {
            return json(
                await apiFetch(url('/exercises'), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        async updateExercise(id: string, input: ExerciseInput): Promise<Exercise> {
            return json(
                await apiFetch(exerciseUrl(id), {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        async archiveExercise(id: string): Promise<void> {
            const response = await apiFetch(exerciseUrl(id), { method: 'DELETE' });
            if (!response.ok) throw await problem(response);
        },
        /** Restrições de mobilidade do aluno vinculado (DEV-0058). */
        async listRestrictions(clientId: string): Promise<MobilityRestriction[]> {
            return json(
                await apiFetch(restrictionsUrl(clientId), { headers: { Accept: 'application/json' } }),
            );
        },
        async createRestriction(
            clientId: string,
            input: MobilityRestrictionInput,
        ): Promise<MobilityRestriction> {
            return json(
                await apiFetch(restrictionsUrl(clientId), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        async updateRestriction(
            clientId: string,
            restrictionId: string,
            input: MobilityRestrictionInput,
        ): Promise<MobilityRestriction> {
            return json(
                await apiFetch(`${restrictionsUrl(clientId)}/${encodeURIComponent(restrictionId)}`, {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        async removeRestriction(clientId: string, restrictionId: string): Promise<void> {
            const response = await apiFetch(
                `${restrictionsUrl(clientId)}/${encodeURIComponent(restrictionId)}`,
                {
                    method: 'DELETE',
                },
            );
            if (!response.ok) throw await problem(response);
        },
        /** Fichas publicadas que vencem nos próximos dias ou já venceram (DEV-0059). */
        async expiringPlans(): Promise<ExpiringPlan[]> {
            return json(await apiFetch(url('/plans/expiring'), { headers: { Accept: 'application/json' } }));
        },
        /** Fichas que o personal criou para o aluno vinculado (DEV-0059). */
        async listPlans(clientId: string): Promise<WorkoutPlan[]> {
            return json(
                await apiFetch(url(`/clients/${encodeURIComponent(clientId)}/plans`), {
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        /** Aderência e evolução de carga do aluno vinculado de `from` até hoje (DEV-0091); sem vínculo, acesso acabou. */
        async clientProgress(clientId: string, from: string): Promise<TrainingProgress> {
            return readLinked(
                url(
                    `/clients/${encodeURIComponent(clientId)}/progress/training?from=${encodeURIComponent(from)}`,
                ),
            );
        },
        /** Recordes do aluno vinculado por exercício (DEV-0068). */
        async clientRecords(clientId: string): Promise<ExerciseRecords[]> {
            return readLinked(url(`/clients/${encodeURIComponent(clientId)}/records`));
        },
        async plan(planId: string): Promise<WorkoutPlan> {
            return json(await apiFetch(planUrl(planId), { headers: { Accept: 'application/json' } }));
        },
        async createPlan(clientId: string, input: WorkoutPlanInput): Promise<WorkoutPlan> {
            return json(
                await apiFetch(url(`/clients/${encodeURIComponent(clientId)}/plans`), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        /** Rascunho muda no lugar; ficha publicada vira a versão seguinte (DEV-0061, com `baseVersion`). */
        async updatePlan(planId: string, input: WorkoutPlanInput): Promise<WorkoutPlan> {
            return json(
                await apiFetch(planUrl(planId), {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(input),
                }),
            );
        },
        async publishPlan(planId: string): Promise<WorkoutPlan> {
            return json(
                await apiFetch(`${planUrl(planId)}/publish`, {
                    method: 'POST',
                    headers: { Accept: 'application/json' },
                }),
            );
        },
    };

    async function readLinked<T>(target: string): Promise<T> {
        const response = await apiFetch(target, { headers: { Accept: 'application/json' } });
        if (response.status === 404) throw new AccessEndedError();
        return json(response);
    }
}

export type TrainingApi = ReturnType<typeof trainingApi>;
