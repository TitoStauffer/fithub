import { useMemo } from 'react';

import { useApiFetch } from '@/features/auth/useApiFetch';
import { config } from '@/lib/config';
import { trainingApi, type TrainingApi } from '@/lib/trainingApi';

/** Cliente do training-service ligado à sessão atual. */
export function useTrainingApi(): TrainingApi {
    const apiFetch = useApiFetch();
    return useMemo(() => trainingApi(apiFetch, config.trainingApiUrl), [apiFetch]);
}

export const EXERCISES_KEY = ['personal', 'exercises'] as const;
