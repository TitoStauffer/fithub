import { useMemo } from 'react';

import { useApiFetch } from '@/features/auth/useApiFetch';
import { config } from '@/lib/config';
import { nutritionApi, type NutritionApi } from '@/lib/nutritionApi';

/** Cliente do nutrition-service ligado à sessão atual. */
export function useNutritionApi(): NutritionApi {
    const apiFetch = useApiFetch();
    return useMemo(() => nutritionApi(apiFetch, config.nutritionApiUrl), [apiFetch]);
}

export const FOODS_KEY = ['nutritionist', 'foods'] as const;
