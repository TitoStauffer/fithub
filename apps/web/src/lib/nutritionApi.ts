import type { NutritionServiceComponents } from '@fithub/api-types';

import { problem } from './api';
import { AccessEndedError } from './clientApi';
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
export type MealPlan = Schemas['MealPlan'];
export type FoodRestriction = Schemas['FoodRestriction'];
export type FoodRestrictionInput = Schemas['FoodRestrictionInput'];
export type FoodRestrictionKind = FoodRestrictionInput['kind'];
export type MealPlanInput = Schemas['MealPlanInput'];
export type MealItemAlert = Schemas['MealItemAlert'];
export type NutrientAmounts = Schemas['NutrientAmounts'];
export type DietProgress = Schemas['DietProgress'];
export type PatientActivity = Schemas['PatientActivity'];

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

/**
 * Base de alimentos (DEV-0074 a DEV-0078), restrições alimentares (DEV-0080) e planos alimentares (DEV-0081 a DEV-0084)
 * no nutrition-service.
 */
export function nutritionApi(apiFetch: ApiFetch, baseUrl: string) {
    const url = (path: string) => `${baseUrl}${path}`;
    const foodUrl = (id: string) => url(`/foods/${encodeURIComponent(id)}`);
    const mealPlanUrl = (id: string) => url(`/meal-plans/${encodeURIComponent(id)}`);
    const restrictionsUrl = (clientId: string) =>
        url(`/clients/${encodeURIComponent(clientId)}/food-restrictions`);
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
        async listFoodRestrictions(clientId: string): Promise<FoodRestriction[]> {
            return json(
                await apiFetch(restrictionsUrl(clientId), { headers: { Accept: 'application/json' } }),
            );
        },
        async createFoodRestriction(clientId: string, body: FoodRestrictionInput): Promise<FoodRestriction> {
            return json(
                await apiFetch(restrictionsUrl(clientId), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async updateFoodRestriction(
            clientId: string,
            restrictionId: string,
            body: FoodRestrictionInput,
        ): Promise<FoodRestriction> {
            return json(
                await apiFetch(`${restrictionsUrl(clientId)}/${encodeURIComponent(restrictionId)}`, {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async removeFoodRestriction(clientId: string, restrictionId: string): Promise<void> {
            const response = await apiFetch(
                `${restrictionsUrl(clientId)}/${encodeURIComponent(restrictionId)}`,
                {
                    method: 'DELETE',
                },
            );
            if (!response.ok) throw await problem(response);
        },
        async listMealPlans(clientId: string): Promise<MealPlan[]> {
            return json(
                await apiFetch(url(`/clients/${encodeURIComponent(clientId)}/meal-plans`), {
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        async mealPlan(id: string): Promise<MealPlan> {
            return json(await apiFetch(mealPlanUrl(id), { headers: { Accept: 'application/json' } }));
        },
        async createMealPlan(clientId: string, body: MealPlanInput): Promise<MealPlan> {
            return json(
                await apiFetch(url(`/clients/${encodeURIComponent(clientId)}/meal-plans`), {
                    method: 'POST',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async updateMealPlan(id: string, body: MealPlanInput): Promise<MealPlan> {
            return json(
                await apiFetch(mealPlanUrl(id), {
                    method: 'PUT',
                    headers: JSON_HEADERS,
                    body: JSON.stringify(body),
                }),
            );
        },
        async publishMealPlan(id: string): Promise<MealPlan> {
            return json(
                await apiFetch(`${mealPlanUrl(id)}/publish`, {
                    method: 'POST',
                    headers: { Accept: 'application/json' },
                }),
            );
        },
        /**
         * Aderência, alternativas mais usadas e desvios do paciente vinculado de `from` até hoje (DEV-0092), nos planos
         * da nutricionista; sem vínculo, o acesso acabou.
         */
        async clientDietProgress(clientId: string, from: string): Promise<DietProgress> {
            const response = await apiFetch(
                url(
                    `/clients/${encodeURIComponent(clientId)}/progress/nutrition?from=${encodeURIComponent(from)}`,
                ),
                { headers: { Accept: 'application/json' } },
            );
            if (response.status === 404) throw new AccessEndedError();
            return json(response);
        },
        /** O último registro de refeição de cada paciente vinculado, nos planos da nutricionista (DEV-0092). */
        async patientsActivity(): Promise<PatientActivity[]> {
            return json(
                await apiFetch(url('/professionals/me/clients/activity'), {
                    headers: { Accept: 'application/json' },
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
