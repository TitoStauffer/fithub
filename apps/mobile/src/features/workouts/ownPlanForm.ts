import {
    createOwnPlan,
    fetchCatalogExercise,
    fetchOwnPlans,
    publishOwnPlan,
    searchCatalog,
    updateOwnPlan,
    type WorkoutPlan,
    type WorkoutPlanInput,
} from '../../lib/api';
import type { ApiFetch } from '../../lib/apiFetch';
import { isoDate } from './RecordsScreen';
import { formatDate } from './workoutsApi';

/** O que o editor da ficha própria precisa da API; injetável nos testes (DEV-0073). */
export interface OwnPlanApi {
    list(): Promise<WorkoutPlan[]>;
    create(body: WorkoutPlanInput): Promise<WorkoutPlan>;
    update(planId: string, body: WorkoutPlanInput): Promise<WorkoutPlan>;
    publish(planId: string): Promise<WorkoutPlan>;
    searchExercises(name: string): Promise<{ id?: string; name?: string }[]>;
    exerciseName(exerciseId: string): Promise<string | undefined>;
}

export function ownPlanApiFrom(apiFetch: ApiFetch): OwnPlanApi {
    return {
        list: () => fetchOwnPlans(apiFetch),
        create: (body) => createOwnPlan(apiFetch, body),
        update: (planId, body) => updateOwnPlan(apiFetch, planId, body),
        publish: (planId) => publishOwnPlan(apiFetch, planId),
        searchExercises: (name) => searchCatalog(apiFetch, name),
        exerciseName: async (exerciseId) => (await fetchCatalogExercise(apiFetch, exerciseId))?.name,
    };
}

export const MAX_DIVISIONS = 7;

/** Um exercício da divisão como a tela edita: tudo em texto, para o aluno digitar. */
export interface ItemForm {
    exerciseId: string;
    exerciseName: string;
    sets: string;
    repsMin: string;
    repsMax: string;
    loadKg: string;
    restSeconds: string;
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

export function letterOf(position: number): string {
    return String.fromCharCode(65 + position);
}

export function newItem(exercise: { id?: string; name?: string }): ItemForm {
    return {
        exerciseId: exercise.id ?? '',
        exerciseName: exercise.name ?? '',
        sets: '3',
        repsMin: '10',
        repsMax: '',
        loadKg: '',
        restSeconds: '',
    };
}

export function emptyForm(today: string): PlanForm {
    return { name: '', startsOn: today, validityWeeks: '8', divisions: [{ name: '', items: [] }] };
}

const text = (value: number | null | undefined) => (value == null ? '' : String(value).replace('.', ','));

/** A ficha da API no formato do formulário; o nome de cada exercício vem de {@code names}. */
export function fromPlan(plan: WorkoutPlan, names: Map<string, string>): PlanForm {
    return {
        name: plan.name ?? '',
        startsOn: formatDate(plan.startsOn),
        validityWeeks: String(plan.validityWeeks ?? ''),
        divisions: (plan.divisions ?? []).map((division) => ({
            name: division.name ?? '',
            items: (division.items ?? []).map((item) => ({
                exerciseId: item.exerciseId ?? '',
                exerciseName: names.get(item.exerciseId ?? '') ?? 'Exercício',
                sets: String(item.sets ?? ''),
                repsMin: String(item.repsMin ?? ''),
                repsMax: item.repsMax != null && item.repsMax !== item.repsMin ? String(item.repsMax) : '',
                loadKg: text(item.loadKg),
                restSeconds: text(item.restSeconds),
            })),
        })),
    };
}

const integer = (value: string) => (/^\d+$/.test(value.trim()) ? Number(value.trim()) : undefined);

/**
 * Valida como a API (PlanVersion e PlanItem), com os erros nos mesmos caminhos que ela devolve
 * ({@code divisions[0].items[1].sets}), para o erro aparecer no campo do item.
 */
export function validate(form: PlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    const name = form.name.trim();
    if (name.length < 2 || name.length > 80)
        errors.name = 'O nome da ficha precisa ter de 2 a 80 caracteres.';
    if (!isoDate(form.startsOn)) errors.startsOn = 'Use a data no formato dd/mm/aaaa.';
    const weeks = integer(form.validityWeeks);
    if (weeks === undefined || weeks < 1 || weeks > 52)
        errors.validityWeeks = 'A validade vai de 1 a 52 semanas.';
    if (form.divisions.length < 1 || form.divisions.length > MAX_DIVISIONS) {
        errors.divisions = 'A ficha tem de 1 a 7 divisões.';
    }
    form.divisions.forEach((division, d) => {
        if (division.name.trim().length > 60) {
            errors[`divisions[${d}].name`] = 'O nome da divisão pode ter até 60 caracteres.';
        }
        division.items.forEach((item, i) => {
            const path = `divisions[${d}].items[${i}]`;
            const sets = integer(item.sets);
            if (sets === undefined || sets < 1 || sets > 20)
                errors[`${path}.sets`] = 'As séries vão de 1 a 20.';
            const min = integer(item.repsMin);
            if (min === undefined || min < 1 || min > 100) {
                errors[`${path}.repsMin`] = 'As repetições vão de 1 a 100.';
            }
            if (item.repsMax.trim() !== '') {
                const max = integer(item.repsMax);
                if (max === undefined || max > 100 || (min !== undefined && max < min)) {
                    errors[`${path}.repsMax`] = 'O máximo da faixa vai do mínimo até 100.';
                }
            }
            if (item.loadKg.trim() !== '') {
                const load = Number(item.loadKg.trim().replace(',', '.'));
                const valid =
                    /^\d+([.,]\d+)?$/.test(item.loadKg.trim()) && load <= 1000 && (load * 2) % 1 === 0;
                if (!valid) errors[`${path}.loadKg`] = 'A carga vai de 0 a 1000 kg, em passos de 0,5.';
            }
            if (item.restSeconds.trim() !== '') {
                const rest = integer(item.restSeconds);
                if (rest === undefined || rest > 600) {
                    errors[`${path}.restSeconds`] = 'O descanso vai de 0 a 600 segundos.';
                }
            }
        });
    });
    return errors;
}

/** Publicar exige ao menos um exercício em cada divisão (BR-10), no mesmo caminho do erro da API. */
export function validateForPublish(form: PlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    form.divisions.forEach((division, d) => {
        if (division.items.length === 0) {
            errors[`divisions[${d}].items`] = 'Cada divisão precisa de ao menos um exercício.';
        }
    });
    return errors;
}

/** O formulário (já validado) no corpo da API; sem técnica nem alternativas no app (DEV-0073). */
export function toInput(form: PlanForm, baseVersion: number | null): WorkoutPlanInput {
    return {
        baseVersion,
        name: form.name.trim(),
        startsOn: isoDate(form.startsOn) ?? '',
        validityWeeks: Number(form.validityWeeks.trim()),
        divisions: form.divisions.map((division) => ({
            name: division.name.trim() === '' ? null : division.name.trim(),
            items: division.items.map((item) => ({
                exerciseId: item.exerciseId,
                sets: Number(item.sets.trim()),
                repsMin: Number(item.repsMin.trim()),
                repsMax: item.repsMax.trim() === '' ? null : Number(item.repsMax.trim()),
                loadKg: item.loadKg.trim() === '' ? null : Number(item.loadKg.trim().replace(',', '.')),
                restSeconds: item.restSeconds.trim() === '' ? null : Number(item.restSeconds.trim()),
                note: null,
                technique: null,
                alternatives: [],
            })),
        })),
    };
}
