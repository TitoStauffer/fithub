import type { ProfessionalRegistrationRequest } from '@/lib/api';

/** UFs aceitas pelo account-service (Registration.java). */
export const STATES = [
    'AC',
    'AL',
    'AP',
    'AM',
    'BA',
    'CE',
    'DF',
    'ES',
    'GO',
    'MA',
    'MT',
    'MS',
    'MG',
    'PA',
    'PB',
    'PR',
    'PE',
    'PI',
    'RJ',
    'RN',
    'RS',
    'RO',
    'RR',
    'SC',
    'SP',
    'SE',
    'TO',
] as const;

export type ProfessionalType = ProfessionalRegistrationRequest['type'];

export interface RegistrationForm {
    type: ProfessionalType | '';
    registrationNumber: string;
    registrationCategory: string;
    registrationState: string;
}

export const EMPTY_FORM: RegistrationForm = {
    type: '',
    registrationNumber: '',
    registrationCategory: '',
    registrationState: '',
};

/**
 * Mesmas regras do account-service (DEV-0029), para avisar antes de enviar. A API continua sendo a
 * guarda: o que ela recusar aparece no campo do mesmo jeito.
 */
export function validate(form: RegistrationForm): Record<string, string> {
    const errors: Record<string, string> = {};
    if (form.type === '') errors.type = 'Informe se você é personal ou nutricionista.';
    if (!/^\d{1,6}$/.test(form.registrationNumber.trim())) {
        errors.registrationNumber = 'Número do registro: de 1 a 6 dígitos.';
    }
    if (!(STATES as readonly string[]).includes(form.registrationState)) {
        errors.registrationState = 'UF do registro inválida.';
    }
    if (form.type === 'PERSONAL' && !['G', 'P'].includes(form.registrationCategory)) {
        errors.registrationCategory = 'Categoria do CREF: G ou P.';
    }
    return errors;
}

/** Corpo do POST: o CRN não leva categoria. */
export function toRequest(form: RegistrationForm): ProfessionalRegistrationRequest {
    if (form.type === '') throw new Error('Formulário sem tipo de profissional.');
    return {
        type: form.type,
        registrationNumber: form.registrationNumber.trim(),
        registrationCategory: form.type === 'PERSONAL' ? form.registrationCategory : null,
        registrationState: form.registrationState,
    };
}
