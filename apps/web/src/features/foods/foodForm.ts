import type { BaseUnit, Food, FoodInput, FoodSummary } from '@/lib/nutritionApi';

/** Ingrediente da receita como a tela edita: o alimento escolhido (com os valores por 100) e a quantidade. */
export interface IngredientForm {
    foodId: string;
    name: string;
    amount: string;
    per100: { energyKcal: number; proteinG: number; carbsG: number; fatG: number };
    vegan: boolean;
}

/** O alimento próprio como a tela edita: números em texto, para o usuário digitar com vírgula. */
export interface FoodForm {
    name: string;
    baseUnit: BaseUnit;
    energyKcal: string;
    proteinG: string;
    carbsG: string;
    fatG: string;
    fiberG: string;
    vegan: boolean;
    isRecipe: boolean;
    ingredients: IngredientForm[];
    yieldGrams: string;
}

export const EMPTY_FORM: FoodForm = {
    name: '',
    baseUnit: 'G',
    energyKcal: '',
    proteinG: '',
    carbsG: '',
    fatG: '',
    fiberG: '',
    vegan: false,
    isRecipe: false,
    ingredients: [],
    yieldGrams: '',
};

const text = (value: number | null | undefined) => (value == null ? '' : String(value).replace('.', ','));

/** Número digitado (com vírgula ou ponto); vazio ou inválido, `undefined`. */
export function parseNumber(value: string): number | undefined {
    const trimmed = value.trim();
    return /^\d+([.,]\d+)?$/.test(trimmed) ? Number(trimmed.replace(',', '.')) : undefined;
}

export function per100Of(food: Food | FoodSummary): IngredientForm['per100'] {
    return {
        energyKcal: food.energyKcal ?? 0,
        proteinG: food.proteinG ?? 0,
        carbsG: food.carbsG ?? 0,
        fatG: food.fatG ?? 0,
    };
}

/** O alimento da API no formato do formulário; os ingredientes vêm com os alimentos já lidos. */
export function formOf(food: Food, ingredientFoods: Map<string, Food> = new Map()): FoodForm {
    return {
        name: food.name ?? '',
        baseUnit: food.baseUnit ?? 'G',
        energyKcal: text(food.energyKcal),
        proteinG: text(food.proteinG),
        carbsG: text(food.carbsG),
        fatG: text(food.fatG),
        fiberG: text(food.fiberG),
        vegan: food.vegan ?? false,
        isRecipe: Boolean(food.recipe),
        ingredients: (food.recipe?.ingredients ?? []).map((ingredient) => {
            const used = ingredientFoods.get(ingredient.foodId ?? '');
            return {
                foodId: ingredient.foodId ?? '',
                name: used?.name ?? 'Alimento',
                amount: text(ingredient.amount),
                per100: used ? per100Of(used) : { energyKcal: 0, proteinG: 0, carbsG: 0, fatG: 0 },
                vegan: used?.vegan ?? false,
            };
        }),
        yieldGrams: text(food.recipe?.yieldGrams),
    };
}

function between(errors: Record<string, string>, field: string, value: string, max: number, message: string) {
    const number = parseNumber(value);
    if (number === undefined || number > max) errors[field] = message;
    return number;
}

/** Validação espelhada da API (DEV-0074, DEV-0077), com os mesmos campos que ela devolve. */
export function validate(form: FoodForm): Record<string, string> {
    const errors: Record<string, string> = {};
    const name = form.name.trim();
    if (name.length < 2 || name.length > 120) errors.name = 'O nome precisa ter de 2 a 120 caracteres.';
    if (form.isRecipe) {
        if (form.ingredients.length < 1 || form.ingredients.length > 30) {
            errors['recipe.ingredients'] = 'A receita tem de 1 a 30 ingredientes.';
        }
        form.ingredients.forEach((ingredient, index) => {
            const amount = parseNumber(ingredient.amount);
            if (amount === undefined || amount < 0.1 || amount > 5000) {
                errors[`recipe.ingredients[${index}].amount`] = 'A quantidade vai de 0,1 a 5000 g (ou ml).';
            }
        });
        const yieldGrams = parseNumber(form.yieldGrams);
        if (yieldGrams === undefined || yieldGrams < 1 || yieldGrams > 20000) {
            errors['recipe.yieldGrams'] = 'O rendimento vai de 1 a 20000 g.';
        }
        return errors;
    }
    between(errors, 'energyKcal', form.energyKcal, 900, 'A energia vai de 0 a 900 kcal.');
    const protein = between(errors, 'proteinG', form.proteinG, 100, 'A proteína vai de 0 a 100 g.');
    const carbs = between(errors, 'carbsG', form.carbsG, 100, 'O carboidrato vai de 0 a 100 g.');
    const fat = between(errors, 'fatG', form.fatG, 100, 'A gordura vai de 0 a 100 g.');
    if (form.fiberG.trim() !== '') between(errors, 'fiberG', form.fiberG, 100, 'A fibra vai de 0 a 100 g.');
    if (protein !== undefined && carbs !== undefined && fat !== undefined && protein + carbs + fat > 100) {
        errors.per100 = 'Proteína, carboidrato e gordura somam no máximo 100 g por 100 g.';
    }
    return errors;
}

/**
 * Valores por 100 g da receita, como a API calcula ao salvar: a soma de (valor por 100 × quantidade) dividida pelo
 * rendimento. `undefined` enquanto falta quantidade ou rendimento.
 */
export function recipePreview(form: FoodForm): IngredientForm['per100'] | undefined {
    const yieldGrams = parseNumber(form.yieldGrams);
    if (!yieldGrams || form.ingredients.length === 0) return undefined;
    const totals = { energyKcal: 0, proteinG: 0, carbsG: 0, fatG: 0 };
    for (const ingredient of form.ingredients) {
        const amount = parseNumber(ingredient.amount);
        if (amount === undefined) return undefined;
        totals.energyKcal += ingredient.per100.energyKcal * amount;
        totals.proteinG += ingredient.per100.proteinG * amount;
        totals.carbsG += ingredient.per100.carbsG * amount;
        totals.fatG += ingredient.per100.fatG * amount;
    }
    const round = (value: number, digits: number) =>
        Math.round((value / yieldGrams) * 10 ** digits) / 10 ** digits;
    return {
        energyKcal: round(totals.energyKcal, 1),
        proteinG: round(totals.proteinG, 2),
        carbsG: round(totals.carbsG, 2),
        fatG: round(totals.fatG, 2),
    };
}

/** O formulário (já validado) no corpo da API. */
export function toInput(form: FoodForm): FoodInput {
    if (form.isRecipe) {
        return {
            name: form.name.trim(),
            baseUnit: 'G',
            recipe: {
                ingredients: form.ingredients.map((ingredient) => ({
                    foodId: ingredient.foodId,
                    amount: parseNumber(ingredient.amount) ?? 0,
                })),
                yieldGrams: parseNumber(form.yieldGrams) ?? 0,
            },
        };
    }
    return {
        name: form.name.trim(),
        baseUnit: form.baseUnit,
        energyKcal: parseNumber(form.energyKcal),
        proteinG: parseNumber(form.proteinG),
        carbsG: parseNumber(form.carbsG),
        fatG: parseNumber(form.fatG),
        fiberG: form.fiberG.trim() === '' ? undefined : parseNumber(form.fiberG),
        vegan: form.vegan,
    };
}

/** Valores por 100 no formato da tela: "128 kcal · P 2,5 g · C 28,1 g · G 0,2 g". */
export function per100Text(values: {
    energyKcal?: number;
    proteinG?: number;
    carbsG?: number;
    fatG?: number;
}) {
    const n = (value: number | undefined) => String(value ?? 0).replace('.', ',');
    return `${n(values.energyKcal)} kcal · P ${n(values.proteinG)} g · C ${n(values.carbsG)} g · G ${n(values.fatG)} g`;
}
