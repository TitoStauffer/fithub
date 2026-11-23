import type { AssessmentRequest } from '@/lib/clientApi';

/** Mesmas regras do client-service (DEV-0044 a 0047), para o erro aparecer antes de enviar. */
export const CIRCUMFERENCES = [
    ['neck', 'Pescoço'],
    ['chest', 'Tórax'],
    ['waist', 'Cintura'],
    ['abdomen', 'Abdômen'],
    ['hip', 'Quadril'],
    ['arm', 'Braço'],
    ['forearm', 'Antebraço'],
    ['thigh', 'Coxa'],
    ['calf', 'Panturrilha'],
] as const;

export const SKINFOLDS = [
    ['chest', 'Peitoral'],
    ['midaxillary', 'Axilar média'],
    ['triceps', 'Tríceps'],
    ['subscapular', 'Subescapular'],
    ['abdomen', 'Abdominal'],
    ['suprailiac', 'Suprailíaca'],
    ['thigh', 'Coxa'],
] as const;

export const ORIGINS: Record<string, string> = {
    POLLOCK_3: 'Pollock 3 dobras',
    POLLOCK_7: 'Pollock 7 dobras',
    NAVY: 'Marinha (online)',
    BIOIMPEDANCE: 'Bioimpedância',
};

/** O que o profissional digita: tudo texto, para nada se perder enquanto ele corrige. */
export type AssessmentForm = Record<string, string>;

export const EMPTY_FORM: AssessmentForm = {
    measuredOn: '',
    weightKg: '',
    sexForFormulas: '',
    bioimpedance: '',
};

function decimal(text: string | undefined): number | undefined {
    const value = (text ?? '').trim().replace(',', '.');
    if (value === '') return undefined;
    return /^\d+(\.\d)?$/.test(value) ? Number(value) : Number.NaN;
}

function checkRange(
    errors: Record<string, string>,
    field: string,
    text: string | undefined,
    min: number,
    max: number,
    message: string,
    required = false,
) {
    const value = decimal(text);
    if (value === undefined) {
        if (required) errors[field] = message;
        return;
    }
    if (Number.isNaN(value) || value < min || value > max) errors[field] = message;
}

export function validate(form: AssessmentForm, today: string): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!/^\d{4}-\d{2}-\d{2}$/.test(form.measuredOn ?? '')) {
        errors.measuredOn = 'Informe a data da medição.';
    } else if ((form.measuredOn ?? '') > today) {
        errors.measuredOn = 'A data da medição não pode estar no futuro.';
    }
    checkRange(
        errors,
        'weightKg',
        form.weightKg,
        20,
        400,
        'O peso vai de 20 a 400 kg, com até 1 casa decimal.',
        true,
    );
    for (const [key] of CIRCUMFERENCES) {
        checkRange(
            errors,
            `circumferences.${key}`,
            form[`c.${key}`],
            10,
            300,
            'De 10 a 300 cm, com até 1 casa decimal.',
        );
    }
    for (const [key] of SKINFOLDS) {
        checkRange(
            errors,
            `skinfolds.${key}`,
            form[`s.${key}`],
            1,
            80,
            'De 1 a 80 mm, com até 1 casa decimal.',
        );
    }
    checkRange(
        errors,
        'bioimpedanceBodyFatPercent',
        form.bioimpedance,
        3,
        70,
        'De 3 a 70%, com até 1 casa decimal.',
    );
    return errors;
}

function group(
    form: AssessmentForm,
    prefix: string,
    keys: readonly (readonly [string, string])[],
): Record<string, number> | undefined {
    const values: Record<string, number> = {};
    for (const [key] of keys) {
        const value = decimal(form[`${prefix}.${key}`]);
        if (value !== undefined) values[key] = value;
    }
    return Object.keys(values).length > 0 ? values : undefined;
}

export function toRequest(form: AssessmentForm): AssessmentRequest {
    const request: AssessmentRequest = {
        measuredOn: form.measuredOn ?? '',
        weightKg: decimal(form.weightKg) ?? 0,
    };
    if (form.sexForFormulas === 'FEMALE' || form.sexForFormulas === 'MALE') {
        request.sexForFormulas = form.sexForFormulas;
    }
    const circumferences = group(form, 'c', CIRCUMFERENCES);
    if (circumferences) request.circumferences = circumferences;
    const skinfolds = group(form, 's', SKINFOLDS);
    if (skinfolds) request.skinfolds = skinfolds;
    const bioimpedance = decimal(form.bioimpedance);
    if (bioimpedance !== undefined) request.bioimpedanceBodyFatPercent = bioimpedance;
    return request;
}
