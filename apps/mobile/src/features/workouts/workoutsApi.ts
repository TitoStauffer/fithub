import { randomUUID } from 'expo-crypto';

import {
    addManualRecord,
    fetchActivePlans,
    fetchMyRecords,
    fetchSessionHistory,
    searchCatalog,
    fetchWorkoutSession,
    finishWorkoutSession,
    recordWorkoutSet,
    removeWorkoutSet,
    saveSessionFeedback,
    startWorkoutSession,
    type ActivePlan,
    type ClientWorkoutItem,
    type ExerciseRecords,
    type ManualRecord,
    type ManualRecordInput,
    type RecordedSet,
    type RecordedSetInput,
    type SessionFeedback,
    type SessionHistoryPage,
    type SessionView,
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

/** O que o registro do treino precisa da API; injetável nos testes. */
export interface SessionApi {
    view(sessionId: string): Promise<SessionView>;
    /** O id da série vem do aparelho: repetir o envio corrige, não duplica. */
    record(sessionId: string, setId: string, body: RecordedSetInput): Promise<RecordedSet>;
    remove(sessionId: string, setId: string): Promise<void>;
    feedback(sessionId: string, body: SessionFeedback): Promise<SessionView>;
    finish(sessionId: string): Promise<WorkoutSession>;
}

export function sessionApiFrom(apiFetch: ApiFetch): SessionApi {
    return {
        view: (sessionId) => fetchWorkoutSession(apiFetch, sessionId),
        record: (sessionId, setId, body) => recordWorkoutSet(apiFetch, sessionId, setId, body),
        remove: (sessionId, setId) => removeWorkoutSet(apiFetch, sessionId, setId),
        feedback: (sessionId, body) => saveSessionFeedback(apiFetch, sessionId, body),
        finish: (sessionId) => finishWorkoutSession(apiFetch, sessionId),
    };
}

/** O que o histórico e os recordes precisam da API; injetável nos testes (DEV-0072). */
export interface HistoryApi {
    history(page: number): Promise<SessionHistoryPage>;
    records(): Promise<ExerciseRecords[]>;
    addRecord(body: ManualRecordInput): Promise<ManualRecord>;
    /** Exercícios do catálogo pelo nome, para escolher o do recorde manual. */
    searchExercises(name: string): Promise<{ id?: string; name?: string }[]>;
}

export function historyApiFrom(apiFetch: ApiFetch): HistoryApi {
    return {
        history: (page) => fetchSessionHistory(apiFetch, page),
        records: () => fetchMyRecords(apiFetch),
        addRecord: (body) => addManualRecord(apiFetch, body),
        searchExercises: (name) => searchCatalog(apiFetch, name),
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
