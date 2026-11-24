import { randomUUID } from 'expo-crypto';

import {
    fetchActivePlans,
    startWorkoutSession,
    type ActivePlan,
    type ClientWorkoutItem,
    type WorkoutSession,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';

/** O que a tela do treino precisa da API; injetável nos testes. */
export interface WorkoutsApi {
    activePlans(): Promise<ActivePlan[]>;
    /** Inicia o próximo treino da ficha; o id vem do aparelho, para o toque duplo não abrir duas sessões. */
    start(planId: string, sessionId: string): Promise<WorkoutSession>;
}

export function workoutsApiFrom(apiFetch: ApiFetch): WorkoutsApi {
    return {
        activePlans: () => fetchActivePlans(apiFetch),
        start: (planId, sessionId) => startWorkoutSession(apiFetch, sessionId, planId),
    };
}

/** Id da sessão gerado no aparelho (UUID v4). */
export function newSessionId(): string {
    return randomUUID();
}

export const TECHNIQUE_LABELS: Record<string, string> = {
    BI_SET: 'Bi-set',
    TRI_SET: 'Tri-set',
    DROP_SET: 'Drop-set',
    REST_PAUSE: 'Rest-pause',
};

/** "3 × 10" ou, com faixa, "3 × 8–12". */
export function setsAndReps(item: ClientWorkoutItem): string {
    const min = item.repsMin ?? 0;
    const max = item.repsMax ?? min;
    return `${item.sets ?? 0} × ${min === max ? min : `${min}–${max}`}`;
}

/** Carga em kg no formato brasileiro: 42,5 kg. */
export function formatKg(value: number): string {
    // Sem Intl: nem todo motor JavaScript do aparelho traz os formatos de pt-BR.
    return `${String(value).replace('.', ',')} kg`;
}

/** Data ISO (aaaa-mm-dd) como dd/mm/aaaa. */
export function formatDate(iso: string | undefined): string {
    if (!iso) return '';
    const [year, month, day] = iso.split('-');
    return `${day}/${month}/${year}`;
}
