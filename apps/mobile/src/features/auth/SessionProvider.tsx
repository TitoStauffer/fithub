import { createContext, useContext, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';

import { createApiFetch, type ApiFetch } from '../../lib/apiFetch';
import { apiBases } from './authConfig';
import type { Session, SessionStatus } from './session';

interface SessionContextValue {
    status: SessionStatus;
    session: Session;
    apiFetch: ApiFetch;
}

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

/** Liga a sessão (DEV-0028) às telas: retoma a sessão guardada ao abrir o app e expõe o estado. */
export function SessionProvider({ session, children }: { session: Session; children: ReactNode }) {
    const status = useSyncExternalStore(session.subscribe, session.status);

    useEffect(() => {
        void session.restore();
    }, [session]);

    const value = useMemo<SessionContextValue>(
        () => ({
            status,
            session,
            apiFetch: createApiFetch({
                getAccessToken: (options) => session.getAccessToken(options),
                onSessionRejected: () => session.reject(),
                allowedBases: apiBases(),
            }),
        }),
        [session, status],
    );

    return <SessionContext value={value}>{children}</SessionContext>;
}

export function useSession(): SessionContextValue {
    const value = useContext(SessionContext);
    if (!value) throw new Error('useSession precisa estar dentro de <SessionProvider>.');
    return value;
}
