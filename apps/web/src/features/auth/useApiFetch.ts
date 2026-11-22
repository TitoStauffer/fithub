import { useMemo } from 'react';

import { apiOrigins } from '@/lib/config';
import { createApiFetch, type ApiFetch } from '@/lib/http';

import { useAuth } from './authContext';

/** `fetch` autenticado para as APIs do FitHub, ligado à sessão atual. */
export function useApiFetch(): ApiFetch {
    const { getAccessToken, endSession } = useAuth();
    return useMemo(
        () => createApiFetch({ getAccessToken, onSessionRejected: endSession, allowedOrigins: apiOrigins() }),
        [getAccessToken, endSession],
    );
}
