import type { ClientProfile, ClientProfileRequest, HealthProfile, HealthProfileRequest } from '../../lib/api';

/** Mesmas regras do client-service (DEV-0032 e DEV-0033), para o erro aparecer antes de enviar. */
export const MINIMUM_AGE = 18;
export const MAX_NAME = 200;
export const MIN_HEIGHT_CM = 100;
export const MAX_HEIGHT_CM = 250;
export const MAX_NOTES = 1000;

export type Gender = ClientProfileRequest['gender'];
export type Goal = ClientProfileRequest['goal'];
export type Condition = NonNullable<HealthProfileRequest['conditions']>[number];

export const GENDERS: { value: Gender; label: string }[] = [
    { value: 'FEMALE', label: 'Feminino' },
    { value: 'MALE', label: 'Masculino' },
    { value: 'OTHER', label: 'Outro' },
    { value: 'PREFER_NOT_TO_SAY', label: 'Prefiro não informar' },
];

export const GOALS: { value: Goal; label: string }[] = [
    { value: 'WEIGHT_LOSS', label: 'Emagrecimento' },
    { value: 'HYPERTROPHY', label: 'Hipertrofia' },
    { value: 'CONDITIONING', label: 'Condicionamento' },
    { value: 'HEALTH', label: 'Saúde' },
    { value: 'OTHER', label: 'Outro' },
];

export const CONDITIONS: { value: Condition; label: string }[] = [
    { value: 'DIABETES', label: 'Diabetes' },
    { value: 'HYPERTENSION', label: 'Hipertensão' },
    { value: 'PREGNANCY', label: 'Gestação' },
    { value: 'RECENT_SURGERY', label: 'Cirurgia recente' },
    { value: 'OTHER', label: 'Outra' },
];

/** O que a pessoa digita: tudo texto, para nada se perder enquanto ela corrige. */
export interface ProfileForm {
    name: string;
    /** DD/MM/AAAA. */
    birthDate: string;
    gender: Gender | '';
    heightCm: string;
    goal: Goal | '';
}

export const EMPTY_PROFILE: ProfileForm = { name: '', birthDate: '', gender: '', heightCm: '', goal: '' };

export type ProfileErrors = Partial<Record<keyof ProfileForm, string>>;

/** DD/MM/AAAA para AAAA-MM-DD; `undefined` se não for uma data de verdade. */
export function toIsoDate(text: string): string | undefined {
    const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text.trim());
    if (!match) return undefined;
    const [, day = '', month = '', year = ''] = match;
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    const valid =
        date.getUTCFullYear() === Number(year) &&
        date.getUTCMonth() === Number(month) - 1 &&
        date.getUTCDate() === Number(day);
    return valid ? `${year}-${month}-${day}` : undefined;
}

function fromIsoDate(iso: string): string {
    const [year = '', month = '', day = ''] = iso.split('-');
    return `${day}/${month}/${year}`;
}

function ageOn(isoBirthDate: string, today: Date): number {
    const [year = 0, month = 0, day = 0] = isoBirthDate.split('-').map(Number);
    let age = today.getFullYear() - year;
    const beforeBirthday =
        today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
    if (beforeBirthday) age -= 1;
    return age;
}

function todayIso(today: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
}

export function validateProfile(form: ProfileForm, today: Date): ProfileErrors {
    const errors: ProfileErrors = {};
    const name = form.name.trim();
    if (name.length === 0 || name.length > MAX_NAME) errors.name = 'Informe seu nome (até 200 caracteres).';
    const birthDate = toIsoDate(form.birthDate);
    if (!birthDate) {
        errors.birthDate = 'Informe a data de nascimento no formato DD/MM/AAAA.';
    } else if (birthDate >= todayIso(today)) {
        errors.birthDate = 'A data de nascimento precisa estar no passado.';
    } else if (ageOn(birthDate, today) < MINIMUM_AGE) {
        errors.birthDate = `No momento o FitHub é só para maiores de ${MINIMUM_AGE} anos.`;
    }
    if (form.gender === '') errors.gender = 'Informe o gênero (ou prefira não informar).';
    const height = Number(form.heightCm);
    if (!/^\d+$/.test(form.heightCm.trim()) || height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM) {
        errors.heightCm = 'Altura em centímetros, de 100 a 250.';
    }
    if (form.goal === '') errors.goal = 'Informe seu objetivo.';
    return errors;
}

export function toProfileRequest(form: ProfileForm): ClientProfileRequest {
    return {
        name: form.name.trim(),
        birthDate: toIsoDate(form.birthDate) ?? '',
        gender: form.gender as Gender,
        heightCm: Number(form.heightCm),
        goal: form.goal as Goal,
    };
}

export function fromProfile(profile: ClientProfile): ProfileForm {
    return {
        name: profile.name ?? '',
        birthDate: profile.birthDate ? fromIsoDate(profile.birthDate) : '',
        gender: profile.gender ?? '',
        heightCm: profile.heightCm === undefined ? '' : String(profile.heightCm),
        goal: profile.goal ?? '',
    };
}

export interface HealthForm {
    conditions: Condition[];
    notes: string;
}

export const EMPTY_HEALTH: HealthForm = { conditions: [], notes: '' };

export function validateHealth(form: HealthForm): Partial<Record<keyof HealthForm, string>> {
    return form.notes.trim().length > MAX_NOTES
        ? { notes: 'O texto livre pode ter até 1000 caracteres.' }
        : {};
}

export function toHealthRequest(form: HealthForm): HealthProfileRequest {
    const notes = form.notes.trim();
    return { conditions: form.conditions, ...(notes.length > 0 ? { notes } : {}) };
}

export function fromHealth(health: HealthProfile): HealthForm {
    return { conditions: [...(health.conditions ?? [])], notes: health.notes ?? '' };
}
