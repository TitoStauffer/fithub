import { useMemo } from 'react';

import { useApiFetch } from '@/features/auth/useApiFetch';
import { clientApi, type ClientApi } from '@/lib/clientApi';
import { config } from '@/lib/config';

/** Cliente do client-service ligado à sessão atual. */
export function useClientApi(): ClientApi {
    const apiFetch = useApiFetch();
    return useMemo(() => clientApi(apiFetch, config.clientApiUrl), [apiFetch]);
}

export const CLIENTS_KEY = ['professional', 'clients'] as const;
export const INVITATIONS_KEY = ['professional', 'invitations'] as const;
export const INVITE_CODE_KEY = ['professional', 'invite-code'] as const;
