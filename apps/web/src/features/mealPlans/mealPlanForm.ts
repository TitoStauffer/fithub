import type { MealPlan, MealPlanInput, NutrientAmounts } from '@/lib/nutritionApi';

/** Nome do estado do plano na tela. */
export const STATUS_LABELS: Record<string, string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    REPLACED: 'Substituído',
};

/** Chave dos planos do paciente no cache. */
export const mealPlansKey = (clientId: string) => ['nutritionist', 'client', clientId, 'meal-plans'] as const;

/** Alimento escolhido na busca: o id vai para a API; o nome e a unidade são só para a tela. */
export interface PickedFood {
    id: string;
    name: string;
    baseUnit: string;
}

/** Medida caseira escolhida: o id vai para a API; o nome é só para a tela. */
export interface PickedMeasure {
    id: string;
    name: string;
}

export interface AlternativeForm {
    food: PickedFood;
    amount: string;
}

/** Item da refeição como está na tela: os números ficam como texto até o envio. */
export interface ItemForm {
    food: PickedFood;
    amount: string;
    /** Com a medida, a quantidade sai dela (DEV-0081): a tela manda a medida e quantas. */
    measure: PickedMeasure | null;
    measureCount: string;
    alternatives: AlternativeForm[];
}

export interface MealForm {
    name: string;
    suggestedTime: string;
    note: string;
    items: ItemForm[];
}

export interface MealPlanForm {
    name: string;
    startsOn: string;
    note: string;
    meals: MealForm[];
}

/** Limites do nutrition-service (DEV-0081 e DEV-0082), espelhados na tela. */
export const LIMITS = {
    minName: 2,
    maxName: 80,
    maxNote: 500,
    maxMeals: 10,
    minMealName: 2,
    maxMealName: 60,
    maxItems: 30,
    minAmount: 0.1,
    maxAmount: 5000,
    minMeasureCount: 0.1,
    maxMeasureCount: 100,
    maxAlternatives: 2,
} as const;

export const unitLabel = (baseUnit: string) => (baseUnit === 'ML' ? 'ml' : 'g');

export function emptyItem(food: PickedFood): ItemForm {
    return { food, amount: '100', measure: null, measureCount: '', alternatives: [] };
}

export function emptyMeal(): MealForm {
    return { name: '', suggestedTime: '', note: '', items: [] };
}

export function emptyForm(today: string): MealPlanForm {
    return { name: '', startsOn: today, note: '', meals: [emptyMeal()] };
}

const text = (value: number | undefined | null) =>
    value === undefined || value === null ? '' : String(value);

/** O plano salvo como formulário; os nomes vêm na própria resposta. */
export function formOf(plan: MealPlan): MealPlanForm {
    return {
        name: plan.name ?? '',
        startsOn: plan.startsOn ?? '',
        note: plan.note ?? '',
        meals: (plan.meals ?? []).map((meal) => ({
            name: meal.name ?? '',
            suggestedTime: meal.suggestedTime ?? '',
            note: meal.note ?? '',
            items: (meal.items ?? []).map((item) => ({
                food: {
                    id: item.foodId ?? '',
                    name: item.foodName ?? 'Alimento',
                    baseUnit: item.baseUnit ?? 'G',
                },
                amount: text(item.amount),
                measure: item.measureId ? { id: item.measureId, name: item.measureName ?? 'Medida' } : null,
                measureCount: text(item.measureCount),
                alternatives: (item.alternatives ?? []).map((alternative) => ({
                    food: {
                        id: alternative.foodId ?? '',
                        name: alternative.foodName ?? 'Alimento',
                        baseUnit: alternative.baseUnit ?? 'G',
                    },
                    amount: text(alternative.amount),
                })),
            })),
        })),
    };
}

const toNumber = (value: string) => Number(value.trim().replace(',', '.'));

function checkDecimal(
    errors: Record<string, string>,
    key: string,
    value: string,
    min: number,
    max: number,
    message: string,
) {
    const number = toNumber(value);
    if (value.trim() === '' || Number.isNaN(number) || number < min || number > max) errors[key] = message;
}

function validateItem(errors: Record<string, string>, path: string, item: ItemForm) {
    if (item.measure) {
        checkDecimal(
            errors,
            `${path}.measureCount`,
            item.measureCount,
            LIMITS.minMeasureCount,
            LIMITS.maxMeasureCount,
            'A quantidade de medidas vai de 0,1 a 100.',
        );
    } else {
        checkDecimal(
            errors,
            `${path}.amount`,
            item.amount,
            LIMITS.minAmount,
            LIMITS.maxAmount,
            'A quantidade vai de 0,1 a 5000 g (ou ml).',
        );
    }
    if (item.alternatives.length > LIMITS.maxAlternatives) {
        errors[`${path}.alternatives`] = 'Cada alimento tem até 2 alternativas.';
    }
    item.alternatives.forEach((alternative, index) =>
        checkDecimal(
            errors,
            `${path}.alternatives[${index}].amount`,
            alternative.amount,
            LIMITS.minAmount,
            LIMITS.maxAmount,
            'A quantidade vai de 0,1 a 5000 g (ou ml).',
        ),
    );
}

/** Erros por campo, com as chaves que a API usa em `errors[].field` (ex.: `meals[1].items[0].amount`). */
export function validate(form: MealPlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    const name = form.name.trim();
    if (name.length < LIMITS.minName || name.length > LIMITS.maxName) {
        errors.name = 'O nome do plano precisa ter de 2 a 80 caracteres.';
    }
    if (!form.startsOn) errors.startsOn = 'Informe a data de início.';
    if (form.note.trim().length > LIMITS.maxNote)
        errors.note = 'A observação do plano pode ter até 500 caracteres.';
    if (form.meals.length < 1 || form.meals.length > LIMITS.maxMeals) {
        errors.meals = 'O plano tem de 1 a 10 refeições.';
    }
    form.meals.forEach((meal, position) => {
        const path = `meals[${position}]`;
        const mealName = meal.name.trim();
        if (mealName.length < LIMITS.minMealName || mealName.length > LIMITS.maxMealName) {
            errors[`${path}.name`] = 'O nome da refeição precisa ter de 2 a 60 caracteres.';
        }
        if (
            meal.suggestedTime.trim() !== '' &&
            !/^([01]\d|2[0-3]):[0-5]\d$/.test(meal.suggestedTime.trim())
        ) {
            errors[`${path}.suggestedTime`] = 'Informe o horário no formato HH:MM.';
        }
        if (meal.note.trim().length > LIMITS.maxNote) {
            errors[`${path}.note`] = 'A observação da refeição pode ter até 500 caracteres.';
        }
        if (meal.items.length > LIMITS.maxItems)
            errors[`${path}.items`] = 'Cada refeição tem até 30 alimentos.';
        meal.items.forEach((item, index) => validateItem(errors, `${path}.items[${index}]`, item));
    });
    return errors;
}

/** Publicar exige ao menos um alimento em cada refeição (BR-10). */
export function validateForPublishing(form: MealPlanForm): Record<string, string> {
    const errors: Record<string, string> = {};
    form.meals.forEach((meal, position) => {
        if (meal.items.length === 0) {
            errors[`meals[${position}].items`] = 'Cada refeição precisa de ao menos um alimento.';
        }
    });
    return errors;
}

const optional = (value: string) => (value.trim() === '' ? undefined : value.trim());

export function toInput(form: MealPlanForm, baseVersion?: number): MealPlanInput {
    return {
        baseVersion,
        name: form.name.trim(),
        startsOn: form.startsOn,
        note: optional(form.note),
        meals: form.meals.map((meal) => ({
            name: meal.name.trim(),
            suggestedTime: optional(meal.suggestedTime),
            note: optional(meal.note),
            items: meal.items.map((item) => ({
                foodId: item.food.id,
                amount: item.measure ? undefined : toNumber(item.amount),
                measureId: item.measure?.id,
                measureCount: item.measure ? toNumber(item.measureCount) : undefined,
                alternatives: item.alternatives.map((alternative) => ({
                    foodId: alternative.food.id,
                    amount: toNumber(alternative.amount),
                })),
            })),
        })),
    };
}

const decimal = (value: number | undefined) =>
    (value ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Os macros em uma linha: "128,0 kcal · P 2,5 g · C 28,1 g · G 0,2 g". */
export function macrosText(amounts: NutrientAmounts | undefined): string {
    return `${decimal(amounts?.energyKcal)} kcal · P ${decimal(amounts?.proteinG)} g · C ${decimal(
        amounts?.carbsG,
    )} g · G ${decimal(amounts?.fatG)} g`;
}

const signed = (value: number | undefined) => {
    const number = value ?? 0;
    return `${number > 0 ? '+' : ''}${decimal(number)}`;
};

/** A diferença da alternativa para o principal, com sinal: "−46,4 kcal · P +0,2 g · …". */
export function differenceText(amounts: NutrientAmounts | undefined): string {
    return `${signed(amounts?.energyKcal)} kcal · P ${signed(amounts?.proteinG)} g · C ${signed(
        amounts?.carbsG,
    )} g · G ${signed(amounts?.fatG)} g`;
}
