import { SessionUnavailableError } from '@/features/auth/authContext';
import { ApiProblemError } from '@/lib/api';
import { AccessEndedError } from '@/lib/clientApi';

/** Mensagem para a pessoa: a da API, a de sem conexão ou uma genérica. */
export function messageOf(error: unknown): string {
    if (error instanceof AccessEndedError) return error.message;
    if (error instanceof SessionUnavailableError) return error.message;
    if (error instanceof ApiProblemError) return error.message;
    return 'Não foi possível concluir. Tente de novo.';
}

export const GENDERS: Record<string, string> = {
    FEMALE: 'Feminino',
    MALE: 'Masculino',
    OTHER: 'Outro',
    PREFER_NOT_TO_SAY: 'Prefere não informar',
};

export const GOALS: Record<string, string> = {
    WEIGHT_LOSS: 'Emagrecimento',
    HYPERTROPHY: 'Hipertrofia',
    CONDITIONING: 'Condicionamento',
    HEALTH: 'Saúde',
    OTHER: 'Outro',
};

export const CONDITIONS: Record<string, string> = {
    DIABETES: 'Diabetes',
    HYPERTENSION: 'Hipertensão',
    PREGNANCY: 'Gestação',
    RECENT_SURGERY: 'Cirurgia recente',
    OTHER: 'Outra',
};

export const INVITATION_STATES: Record<string, string> = {
    PENDING: 'Pendente',
    EXPIRED: 'Vencido',
    ACCEPTED: 'Aceito',
    CANCELLED: 'Cancelado',
};
