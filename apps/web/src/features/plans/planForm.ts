import type { BodyRegion, Technique, WorkoutPlan, WorkoutPlanInput } from '@/lib/trainingApi';

/** Nome do estado da ficha na tela. */
export const STATUS_LABELS: Record<string, string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicada',
    REPLACED: 'Substituída',
};

/** Chave das fichas do aluno no cache.  */
export const plansKey = (clientId: string) => ['personal', 'client', clientId, 'plans'] as const;

/** Exercício escolhido no catálogo: o id vai para a API; o nome é só para a tela. */
export interface PickedExercise {
    id: string;
    name: string;
}

/** Item da divisão como está na tela: os números ficam como texto até o envio. */
export interface ItemForm {
    exercise: PickedExercise;
    sets: string;
    repsMin: string;
    repsMax: string;
    loadKg: string;
    restSeconds: string;
    note: string;
    technique: Technique | '';
    alternatives: PickedExercise[];
}

export interface DivisionForm {
    name: string;
    items: ItemForm[];
}

export interface PlanForm {
    name: string;
    startsOn: string;
    validityWeeks: string;
    divisions: DivisionForm[];
}

/** Limites do training-service (DEV-0059 e DEV-0060), espelhados na tela. */
export const LIMITS = {
    minName: 2,
    maxName: 80,
    maxWeeks: 52,
    maxDivisions: 7,
    maxDivisionName: 60,
    maxItems: 30,
    maxSets: 20,
    maxReps: 100,
    maxLoadKg: 1000,
    maxRestSeconds: 600,
    maxNote: 500,
    maxAlternatives: 3,
} as const;

export const TECHNIQUES: readonly (readonly [Technique, string])[] = [
    ['BI_SET', 'Bi-set'],
    ['TRI_SET', 'Tri-set'],
    ['DROP_SET', 'Drop-set'],
    ['REST_PAUSE', 'Rest-pause'],
];

const REGION_LABELS: Record<BodyRegion, string> = {
    CERVICAL: 'pescoço',
    SHOULDER: 'ombro',
    ELBOW: 'cotovelo',
    WRIST_HAND: 'punho e mão',
    THORACIC: 'coluna torácica',
    LUMBAR: 'lombar',
    HIP: 'quadril',
    KNEE: 'joelho',
    ANKLE_FOOT: 'tornozelo e pé',
};

export const regionLabel = (region: string) => REGION_LABELS[region as BodyRegion] ?? region;

export const letterOf = (position: number) => String.fromCharCode(65 + position);

export function emptyItem(exercise: PickedExercise): ItemForm {
    return {
        exercise,
        sets: '3',
        repsMin: '10',
        repsMax: '',
        loadKg: '',
        restSeconds: '',
        note: '',
        technique: '',
        alternatives: [],
    };
}

export function emptyForm(today: string): PlanForm {
    return { name: '', startsOn: today, validityWeeks: '8', divisions: [{ name: '', items: [] }] };
}

/** A ficha salva como formulário; os nomes dos exercícios vêm de {@code names} (id → nome). */
export function formOf(plan: WorkoutPlan, names: ReadonlyMap<string, string>): PlanForm {
    const picked = (id: string): PickedExercise => ({ id, name: names.get(id) ?? 'Exercício' });
    return {
        name: plan.name ?? '',
        startsOn: plan.startsOn ?? '',
        validityWeeks: String(plan.validityWeeks ?? ''),
        divisions: (plan.divisions ?? []).map((division) => ({
            name: division.name ?? '',
            items: (division.items ?? []).map((item) => ({
                exercise: picked(item.exerciseId ?? ''),
                sets: String(item.sets ?? ''),
                repsMin: String(item.repsMin ?? ''),
                repsMax:
                    item.repsMax !== undefined && item.repsMax !== item.repsMin ? String(item.repsMax) : '',
                loadKg: item.loadKg === null || item.loadKg === undefined ? '' : String(item.loadKg),
                restSeconds:
                    item.restSeconds === null || item.restSeconds === undefined
                        ? ''
                        : String(item.restSeconds),
                note: item.note ?? '',
                technique: item.technique ?? '',
                alternatives: (item.alternatives ?? []).map(picked),
            })),
        })),
    };
}

/** Todos os exercícios da ficha, principais e alternativas, para buscar os nomes. */
export function exerciseIdsOf(plan: WorkoutPlan): string[] {
    const ids = new Set<string>();
    for (const division of plan.divisions ?? []) {
        for (const item of division.items ?? []) {
            if (item.exerciseId) ids.add(item.exerciseId);
            for (const alternative of item.alternatives ?? []) ids.add(alternative);
        }
    }
    return [...ids];
}

const isInteger = (value: string) => /^\d+$/.test(value.trim());
const toNumber = (value: string) => Number(value.trim().replace(',', '.'));

function checkInteger(
    errors: Record<string, string>,
    key: string,
    value: string,
    min: number,
    max: number,
    message: string,
) {
    if (!isInteger(value) || toNumber(value) < min || toNumber(value) > max) errors[key] = message;
}

function validateItem(errors: Record<string, string>, path: string, item: ItemForm) {
    checkInteger(errors, `${path}.sets`, item.sets, 1, LIMITS.maxSets, 'As séries vão de 1 a 20.');
    checkInteger(errors, `${path}.repsMin`, item.repsMin, 1, LIMITS.maxReps, 'As repetições vão de 1 a 100.');
    if (item.repsMax.trim() !== '') {
        const min = isInteger(item.repsMin) ? toNumber(item.repsMin) : 1;
        checkInteger(
            errors,
            `${path}.repsMax`,
            item.repsMax,
            min,
            LIMITS.maxReps,
            'O máximo da faixa vai do mínimo até 100.',
        );
    }
    if (item.loadKg.trim() !== '') {
        const load = toNumber(item.loadKg);
        if (Number.isNaN(load) || load < 0 || load > LIMITS.maxLoadKg || !Number.isInteger(load * 2)) {
            errors[`${path}.loadKg`] = 'A carga vai de 0 a 1000 kg, em passos de 0,5.';
        }
    }
    if (item.restSeconds.trim() !== '') {
        checkInteger(
            errors,
            `${path}.restSeconds`,
            item.restSeconds,
            0,
            LIMITS.maxRestSeconds,
            'O descanso vai de 0 a 600 segundos.',
        );
    }
    if (item.note.trim().length > LIMITS.maxNote)
        errors[`${path}.note`] = 'A observação pode ter até 500 caracteres.';
    if (item.alternatives.length > LIMITS.maxAlternatives) {
        errors[`${path}.alternatives`] = 'Cada exercício tem até 3 alternativas.';
    }
}

/** Erros por campo, com as chaves que a API usa em `errors[].field` (ex.: `divisions[1].items[0].sets`). */
export function validate(form: PlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    const name = form.name.trim();
    if (name.length < LIMITS.minName || name.length > LIMITS.maxName) {
        errors.name = 'O nome da ficha precisa ter de 2 a 80 caracteres.';
    }
    if (!form.startsOn) errors.startsOn = 'Informe a data de início.';
    checkInteger(
        errors,
        'validityWeeks',
        form.validityWeeks,
        1,
        LIMITS.maxWeeks,
        'A validade vai de 1 a 52 semanas.',
    );
    if (form.divisions.length < 1 || form.divisions.length > LIMITS.maxDivisions) {
        errors.divisions = 'A ficha tem de 1 a 7 divisões.';
    }
    form.divisions.forEach((division, position) => {
        const path = `divisions[${position}]`;
        if (division.name.trim().length > LIMITS.maxDivisionName) {
            errors[`${path}.name`] = 'O nome da divisão pode ter até 60 caracteres.';
        }
        if (division.items.length > LIMITS.maxItems)
            errors[`${path}.items`] = 'Cada divisão tem até 30 exercícios.';
        division.items.forEach((item, index) => validateItem(errors, `${path}.items[${index}]`, item));
    });
    return errors;
}

/** Publicar exige ao menos um exercício em cada divisão (BR-10). */
export function validateForPublishing(form: PlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    form.divisions.forEach((division, position) => {
        if (division.items.length === 0) {
            errors[`divisions[${position}].items`] = 'Cada divisão precisa de ao menos um exercício.';
        }
    });
    return errors;
}

const optionalNumber = (value: string) => (value.trim() === '' ? null : toNumber(value));

export function toInput(form: PlanForm, baseVersion?: number): WorkoutPlanInput {
    return {
        baseVersion: baseVersion ?? null,
        name: form.name.trim(),
        startsOn: form.startsOn,
        validityWeeks: toNumber(form.validityWeeks),
        divisions: form.divisions.map((division) => ({
            name: division.name.trim() === '' ? null : division.name.trim(),
            items: division.items.map((item) => ({
                exerciseId: item.exercise.id,
                sets: toNumber(item.sets),
                repsMin: toNumber(item.repsMin),
                repsMax: optionalNumber(item.repsMax),
                loadKg: optionalNumber(item.loadKg),
                restSeconds: optionalNumber(item.restSeconds),
                note: item.note.trim() === '' ? null : item.note.trim(),
                technique: item.technique === '' ? null : item.technique,
                alternatives: item.alternatives.map((alternative) => alternative.id),
            })),
        })),
    };
}
