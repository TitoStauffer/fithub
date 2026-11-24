import type { Equipment, Exercise, ExerciseInput, MuscleGroup } from '@/lib/trainingApi';

/** Nomes em português dos grupos musculares, na ordem em que aparecem nos filtros e no formulário. */
export const MUSCLE_GROUPS: readonly (readonly [MuscleGroup, string])[] = [
    ['CHEST', 'Peito'],
    ['BACK', 'Costas'],
    ['LOWER_BACK', 'Lombar'],
    ['SHOULDERS', 'Ombros'],
    ['BICEPS', 'Bíceps'],
    ['TRICEPS', 'Tríceps'],
    ['FOREARMS', 'Antebraços'],
    ['ABS', 'Abdômen'],
    ['GLUTES', 'Glúteos'],
    ['QUADRICEPS', 'Quadríceps'],
    ['HAMSTRINGS', 'Posteriores da coxa'],
    ['ADDUCTORS', 'Adutores'],
    ['CALVES', 'Panturrilhas'],
    ['NECK', 'Pescoço'],
    ['FULL_BODY', 'Corpo inteiro'],
];

export const EQUIPMENT: readonly (readonly [Equipment, string])[] = [
    ['BARBELL', 'Barra'],
    ['DUMBBELL', 'Halteres'],
    ['KETTLEBELL', 'Kettlebell'],
    ['MACHINE', 'Máquina'],
    ['CABLE', 'Polia'],
    ['BAND', 'Elástico'],
    ['BODYWEIGHT', 'Peso do corpo'],
    ['OTHER', 'Outro'],
];

const MUSCLE_LABELS = new Map<string, string>(MUSCLE_GROUPS);
const EQUIPMENT_LABELS = new Map<string, string>(EQUIPMENT);

export const muscleLabel = (group: string) => MUSCLE_LABELS.get(group) ?? group;
export const equipmentLabel = (equipment: string) => EQUIPMENT_LABELS.get(equipment) ?? equipment;

/** Limites do modelo de exercício no training-service (DEV-0053), espelhados na tela. */
export const LIMITS = { minName: 2, maxName: 120, maxInstructions: 4000, maxMediaUrl: 500 } as const;

export interface ExerciseForm {
    name: string;
    muscleGroups: MuscleGroup[];
    equipment: Equipment | '';
    instructions: string;
    mediaUrl: string;
}

export const EMPTY_FORM: ExerciseForm = {
    name: '',
    muscleGroups: [],
    equipment: '',
    instructions: '',
    mediaUrl: '',
};

export function formOf(exercise: Exercise): ExerciseForm {
    return {
        name: exercise.name ?? '',
        muscleGroups: [...(exercise.muscleGroups ?? [])],
        equipment: exercise.equipment ?? '',
        instructions: exercise.instructions ?? '',
        mediaUrl: exercise.mediaUrl ?? '',
    };
}

/** Como a API: só https (esquema em minúsculas), com host e sem usuário e senha. */
function isHttpsUrl(value: string): boolean {
    if (value.length > LIMITS.maxMediaUrl || !value.startsWith('https://')) return false;
    try {
        const url = new URL(value);
        return url.hostname.length > 0 && url.username === '' && url.password === '';
    } catch {
        return false;
    }
}

/** Erros por campo, com as chaves que a API usa em `errors[].field`. */
export function validate(form: ExerciseForm): Record<string, string> {
    const errors: Record<string, string> = {};
    const name = form.name.trim();
    if (name.length < LIMITS.minName || name.length > LIMITS.maxName) {
        errors.name = 'O nome precisa ter de 2 a 120 caracteres.';
    }
    if (form.muscleGroups.length === 0) errors.muscleGroups = 'Informe ao menos um grupo muscular.';
    if (!form.equipment) errors.equipment = 'Informe o equipamento.';
    if (form.instructions.trim().length > LIMITS.maxInstructions) {
        errors.instructions = 'As instruções podem ter até 4000 caracteres.';
    }
    const mediaUrl = form.mediaUrl.trim();
    if (mediaUrl.length > 0 && !isHttpsUrl(mediaUrl)) {
        errors.mediaUrl = 'O link da mídia precisa ser um endereço https de até 500 caracteres.';
    }
    return errors;
}

export function toInput(form: ExerciseForm): ExerciseInput {
    const instructions = form.instructions.trim();
    const mediaUrl = form.mediaUrl.trim();
    return {
        name: form.name.trim(),
        muscleGroups: form.muscleGroups,
        equipment: form.equipment as Equipment,
        instructions: instructions.length > 0 ? instructions : null,
        mediaUrl: mediaUrl.length > 0 ? mediaUrl : null,
    };
}
