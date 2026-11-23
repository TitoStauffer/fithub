import { useMemo } from 'react';

import { apiOrigins } from '@/lib/config';
import { createApiFetch, type ApiFetch } from '@/lib/http';

import { useAuth } from './authContext';

/** `fetch` autenticado para as APIs do FitHub, ligado à sessão atual. */
export function useApiFetch(): ApiFetch {
    const { getAccessToken, rejectSession } = useAuth();
    return useMemo(
        () =>
            createApiFetch({
                getAccessToken,
                onSessionRejected: rejectSession,
                allowedOrigins: apiOrigins(),
            }),
        [getAccessToken, rejectSession],
    );
}
