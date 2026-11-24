import type { NutritionServiceComponents } from '@fithub/api-types';

import { problem } from './api';
import type { ApiFetch } from './http';

type Schemas = NutritionServiceComponents['schemas'];

// O OpenAPI do springdoc marca todo campo de resposta como opcional; a tela trata a ausência.
export type Food = Schemas['Food'];
export type FoodSummary = Schemas['FoodSummary'];
export type FoodPage = Schemas['FoodPage'];
export type FoodInput = Schemas['FoodInput'];
export type FoodMeasure = Schemas['FoodMeasure'];
export type FoodMeasureInput = Schemas['FoodMeasureInput'];
export type BaseUnit = NonNullable<Food['baseUnit']>;

/** Filtros da busca (DEV-0078); campo vazio não filtra. */
export interface FoodSearch {
    name?: string;
    vegan?: '' | 'true' | 'false';
    page?: number;
    size?: number;
}

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await problem(response);
    return (await response.json()) as T;
}

const JSON_HEADERS = { Accept: 'application/json', 'Content-Type': 'application/json' };

/** Base de alimentos da nutricionista no nutrition-service (DEV-0074 a DEV-0078). */
export function nutritionApi(apiFetch: ApiFetch, baseUrl: string) {
    const url = (path: string) => `${baseUrl}${path}`;
    const foodUrl = (id: string) => url(`/foods/${encodeURIComponent(id)}`);
    return {
        async searchFoods(search: FoodSearch): Promise<FoodPage> {
            const params = new URLSearchParams();
            if (search.name && search.name.trim().length > 0) params.set('name', search.name.trim());
            if (search.vegan) params.set('vegan', search.vegan);
            if (search.page !== undefined) params.set('page', String(search.page));
            if (search.size !== undefined) params.set('size', String(search.size));
            const query = params.toString();
            return json(
                await apiFetch(url(`/foods${query ? `?${query}` : ''}`), {
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        async food(id: string): Promise<Food> {
            return json(await apiFetch(foodUrl(id), { headers: { Accept: 'application/json' } }));
        },
        async createFood(body: FoodInput): Promise<Food> {
            return json(
                await apiFetch(url('/foods'), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async updateFood(id: string, body: FoodInput): Promise<Food> {
            return json(
                await apiFetch(foodUrl(id), {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async archiveFood(id: string): Promise<Food> {
            return json(
                await apiFetch(`${foodUrl(id)}/archive`, {
                    method: 'POST',
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        async addMeasure(foodId: string, body: FoodMeasureInput): Promise<FoodMeasure> {
            return json(
                await apiFetch(`${foodUrl(foodId)}/measures`, {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async removeMeasure(foodId: string, measureId: string): Promise<void> {
            const response = await apiFetch(`${foodUrl(foodId)}/measures/${encodeURIComponent(measureId)}`, {
                method: 'DELETE',
            });
            if (!response.ok) throw await problem(response);
        },
    };
}

export type NutritionApi = ReturnType<typeof nutritionApi>;
