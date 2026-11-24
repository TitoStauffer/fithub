import type { TrainingServiceComponents } from '@fithub/api-types';

import { problem } from './api';
import type { ApiFetch } from './http';

type Schemas = TrainingServiceComponents['schemas'];

export type Exercise = Schemas['Exercise'];
export type ExercisePage = Schemas['ExercisePage'];
export type ExerciseInput = Schemas['ExerciseInput'];
// O OpenAPI do springdoc marca todo campo de resposta como opcional; a tela trata a ausência.
export type MuscleGroup = NonNullable<Exercise['muscleGroups']>[number];
export type Equipment = NonNullable<Exercise['equipment']>;

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
    };
}

export type TrainingApi = ReturnType<typeof trainingApi>;
