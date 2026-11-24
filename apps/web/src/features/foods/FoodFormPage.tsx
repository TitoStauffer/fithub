import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { ApiProblemError } from '@/lib/api';
import type { Food, FoodSummary } from '@/lib/nutritionApi';

import {
    EMPTY_FORM,
    formOf,
    parseNumber,
    per100Of,
    per100Text,
    recipePreview,
    toInput,
    validate,
    type FoodForm,
} from './foodForm';
import { FOODS_KEY, useNutritionApi } from './useNutritionApi';

/** Cadastro (`/app/alimentos/novo`) ou edição (`/app/alimentos/:foodId/editar`) do alimento ou receita própria. */
export function FoodFormPage() {
    const { foodId } = useParams();
    const api = useNutritionApi();
    const existing = useQuery({
        queryKey: [...FOODS_KEY, 'one', foodId],
        queryFn: () => api.food(foodId ?? ''),
        enabled: Boolean(foodId),
    });
    const ingredientIds = (existing.data?.recipe?.ingredients ?? []).map((each) => each.foodId ?? '');
    const ingredients = useQueries({
        queries: ingredientIds.map((id) => ({
            queryKey: [...FOODS_KEY, 'one', id],
            queryFn: () => api.food(id),
        })),
    });

    if (!foodId) return <FoodFormView />;
    if (existing.isPending || ingredients.some((each) => each.isPending)) {
        return <p role="status">Carregando alimento…</p>;
    }
    if (existing.isError) {
        return (
            <section className="space-y-3">
                <p role="alert">{messageOf(existing.error)}</p>
                <Link className="underline" to="/app/alimentos">
                    Voltar para os alimentos
                </Link>
            </section>
        );
    }
    const used = new Map<string, Food>();
    ingredients.forEach((each) => {
        if (each.data?.id) used.set(each.data.id, each.data);
    });
    return <FoodFormView food={existing.data} ingredientFoods={used} />;
}

function FieldError({ message }: { message?: string }) {
    return message ? <span className="block text-sm text-red-700">{message}</span> : null;
}

/**
 * Formulário do alimento ou receita própria (DEV-0079), com a validação do nutrition-service espelhada; o erro que a
 * API devolver para um campo aparece no campo. Na receita, os valores por 100 g calculados aparecem antes de salvar.
 */
function FoodFormView({ food, ingredientFoods }: { food?: Food; ingredientFoods?: Map<string, Food> }) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [form, setForm] = useState<FoodForm>(food ? formOf(food, ingredientFoods) : EMPTY_FORM);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();

    const save = useMutation({
        mutationFn: () => (food?.id ? api.updateFood(food.id, toInput(form)) : api.createFood(toInput(form))),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: FOODS_KEY });
            void navigate('/app/alimentos');
        },
        onError: (failure) => {
            if (failure instanceof ApiProblemError && Object.keys(failure.fieldErrors).length > 0) {
                setErrors(failure.fieldErrors);
                setFormError('Confira os campos destacados.');
            } else {
                setFormError(messageOf(failure));
            }
        },
    });

    const submit = (event: FormEvent) => {
        event.preventDefault();
        const local = validate(form);
        setErrors(local);
        if (Object.keys(local).length > 0) {
            setFormError('Confira os campos destacados.');
            return;
        }
        setFormError(undefined);
        save.mutate();
    };

    const number = (field: 'energyKcal' | 'proteinG' | 'carbsG' | 'fatG' | 'fiberG', label: string) => (
        <div className="space-y-1">
            <label className="space-y-1">
                <span className="block text-sm font-medium">{label}</span>
                <input
                    className="w-28 rounded border px-2 py-1"
                    inputMode="decimal"
                    value={form[field]}
                    aria-invalid={Boolean(errors[field])}
                    onChange={(event) => setForm({ ...form, [field]: event.target.value })}
                />
            </label>
            <FieldError message={errors[field]} />
        </div>
    );
    const preview = form.isRecipe ? recipePreview(form) : undefined;

    return (
        <section className="space-y-4">
            <h3 className="text-base font-semibold">
                {food ? 'Editar alimento' : 'Novo alimento ou receita'}
            </h3>
            {formError ? <p role="alert">{formError}</p> : null}
            <form onSubmit={submit} className="space-y-4" noValidate>
                <label className="block space-y-1">
                    <span className="block text-sm font-medium">Nome</span>
                    <input
                        className="w-full max-w-md rounded border px-2 py-1"
                        value={form.name}
                        aria-invalid={Boolean(errors.name)}
                        onChange={(event) => setForm({ ...form, name: event.target.value })}
                    />
                </label>
                <FieldError message={errors.name} />
                <label className="flex items-center gap-2">
                    <input
                        type="checkbox"
                        checked={form.isRecipe}
                        onChange={(event) => setForm({ ...form, isRecipe: event.target.checked })}
                    />
                    <span className="text-sm">É uma receita (valores calculados pelos ingredientes)</span>
                </label>
                <FieldError message={errors.recipe} />

                {form.isRecipe ? (
                    <RecipeEditor form={form} errors={errors} onChange={setForm} />
                ) : (
                    <fieldset className="space-y-3">
                        <legend className="text-sm font-medium">Valores por 100</legend>
                        <label className="space-y-1">
                            <span className="block text-sm font-medium">Unidade</span>
                            <select
                                className="rounded border px-2 py-1"
                                value={form.baseUnit}
                                onChange={(event) =>
                                    setForm({ ...form, baseUnit: event.target.value as 'G' | 'ML' })
                                }
                            >
                                <option value="G">Gramas (por 100 g)</option>
                                <option value="ML">Mililitros (por 100 ml)</option>
                            </select>
                        </label>
                        <div className="flex flex-wrap gap-3">
                            {number('energyKcal', 'Energia (kcal)')}
                            {number('proteinG', 'Proteína (g)')}
                            {number('carbsG', 'Carboidrato (g)')}
                            {number('fatG', 'Gordura (g)')}
                            {number('fiberG', 'Fibra (g, opcional)')}
                        </div>
                        <FieldError message={errors.per100} />
                        <label className="flex items-center gap-2">
                            <input
                                type="checkbox"
                                checked={form.vegan}
                                onChange={(event) => setForm({ ...form, vegan: event.target.checked })}
                            />
                            <span className="text-sm">Vegano</span>
                        </label>
                    </fieldset>
                )}
                {preview ? (
                    <p role="status" className="text-sm">
                        Por 100 g da receita: {per100Text(preview)}
                        {form.ingredients.every((each) => each.vegan) ? ' · vegana' : ''}
                    </p>
                ) : null}

                <div className="flex gap-2">
                    <Button type="submit" disabled={save.isPending}>
                        {save.isPending ? 'Salvando…' : 'Salvar'}
                    </Button>
                    <Button asChild variant="ghost">
                        <Link to="/app/alimentos">Cancelar</Link>
                    </Button>
                </div>
            </form>
            {food?.id ? <MeasuresEditor food={food} /> : null}
        </section>
    );
}

/** Ingredientes pela busca, com a quantidade, e o rendimento total. */
function RecipeEditor({
    form,
    errors,
    onChange,
}: {
    form: FoodForm;
    errors: Record<string, string>;
    onChange: (form: FoodForm) => void;
}) {
    const api = useNutritionApi();
    const [query, setQuery] = useState('');
    const [found, setFound] = useState<FoodSummary[]>();
    const [searchError, setSearchError] = useState<string>();

    const search = async () => {
        setSearchError(undefined);
        try {
            const page = await api.searchFoods({ name: query, size: 10 });
            // Receita não entra como ingrediente (DEV-0077).
            setFound((page.items ?? []).filter((each) => !each.recipe));
        } catch (failure) {
            setSearchError(messageOf(failure));
        }
    };

    const add = (food: FoodSummary) => {
        onChange({
            ...form,
            ingredients: [
                ...form.ingredients,
                {
                    foodId: food.id ?? '',
                    name: food.name ?? '',
                    amount: '',
                    per100: per100Of(food),
                    vegan: food.vegan ?? false,
                },
            ],
        });
        setFound(undefined);
        setQuery('');
    };

    return (
        <fieldset className="space-y-3">
            <legend className="text-sm font-medium">Ingredientes</legend>
            <FieldError message={errors['recipe.ingredients']} />
            <ul className="space-y-2">
                {form.ingredients.map((ingredient, index) => (
                    <li key={`${ingredient.foodId}-${index}`} className="flex flex-wrap items-end gap-2">
                        <span className="text-sm">{ingredient.name}</span>
                        <label className="space-y-1">
                            <span className="block text-xs">Quantidade (g ou ml) de {ingredient.name}</span>
                            <input
                                className="w-24 rounded border px-2 py-1"
                                inputMode="decimal"
                                value={ingredient.amount}
                                aria-invalid={Boolean(errors[`recipe.ingredients[${index}].amount`])}
                                onChange={(event) =>
                                    onChange({
                                        ...form,
                                        ingredients: form.ingredients.map((each, other) =>
                                            other === index ? { ...each, amount: event.target.value } : each,
                                        ),
                                    })
                                }
                            />
                        </label>
                        <FieldError message={errors[`recipe.ingredients[${index}].amount`]} />
                        <FieldError message={errors[`recipe.ingredients[${index}].foodId`]} />
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() =>
                                onChange({
                                    ...form,
                                    ingredients: form.ingredients.filter((_, other) => other !== index),
                                })
                            }
                        >
                            Remover {ingredient.name}
                        </Button>
                    </li>
                ))}
            </ul>
            <div className="flex flex-wrap items-end gap-2">
                <label className="space-y-1">
                    <span className="block text-sm">Buscar ingrediente</span>
                    <input
                        className="rounded border px-2 py-1"
                        value={query}
                        onChange={(event) => setQuery(event.target.value)}
                    />
                </label>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={query.trim().length < 2}
                    onClick={() => void search()}
                >
                    Buscar ingrediente
                </Button>
            </div>
            {searchError ? <p role="alert">{searchError}</p> : null}
            {found?.length === 0 ? <p className="text-sm">Nenhum alimento encontrado.</p> : null}
            {found?.map((option) => (
                <Button key={option.id} type="button" variant="outline" size="sm" onClick={() => add(option)}>
                    Adicionar {option.name}
                </Button>
            ))}
            <label className="block space-y-1">
                <span className="block text-sm font-medium">Rendimento total (g)</span>
                <input
                    className="w-28 rounded border px-2 py-1"
                    inputMode="decimal"
                    value={form.yieldGrams}
                    aria-invalid={Boolean(errors['recipe.yieldGrams'])}
                    onChange={(event) => onChange({ ...form, yieldGrams: event.target.value })}
                />
            </label>
            <FieldError message={errors['recipe.yieldGrams']} />
        </fieldset>
    );
}

/** Medidas caseiras da nutricionista no alimento (DEV-0076): só ela vê. */
function MeasuresEditor({ food }: { food: Food }) {
    const api = useNutritionApi();
    const queryClient = useQueryClient();
    const [name, setName] = useState('');
    const [amount, setAmount] = useState('');
    const [error, setError] = useState<string>();
    const refresh = () => queryClient.invalidateQueries({ queryKey: [...FOODS_KEY, 'one', food.id] });
    const add = useMutation({
        mutationFn: () =>
            api.addMeasure(food.id ?? '', { name: name.trim(), amount: parseNumber(amount) ?? 0 }),
        onSuccess: async () => {
            setName('');
            setAmount('');
            setError(undefined);
            await refresh();
        },
        onError: (failure) => setError(messageOf(failure)),
    });
    const remove = useMutation({
        mutationFn: (measureId: string) => api.removeMeasure(food.id ?? '', measureId),
        onSuccess: () => refresh(),
        onError: (failure) => setError(messageOf(failure)),
    });
    const unit = food.baseUnit === 'ML' ? 'ml' : 'g';

    return (
        <section aria-label="Medidas caseiras" className="space-y-2">
            <h4 className="font-medium">Medidas caseiras</h4>
            {(food.measures ?? []).length === 0 ? (
                <p className="text-sm">Nenhuma medida cadastrada.</p>
            ) : null}
            <ul className="space-y-1">
                {(food.measures ?? []).map((measure) => (
                    <li key={measure.id} className="flex items-center gap-2 text-sm">
                        {measure.name}: {String(measure.amount ?? 0).replace('.', ',')} {unit}
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => remove.mutate(measure.id ?? '')}
                        >
                            Apagar {measure.name}
                        </Button>
                    </li>
                ))}
            </ul>
            <div className="flex flex-wrap items-end gap-2">
                <label className="space-y-1">
                    <span className="block text-sm">Nome da medida</span>
                    <input
                        className="rounded border px-2 py-1"
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                    />
                </label>
                <label className="space-y-1">
                    <span className="block text-sm">Vale ({unit})</span>
                    <input
                        className="w-24 rounded border px-2 py-1"
                        inputMode="decimal"
                        value={amount}
                        onChange={(event) => setAmount(event.target.value)}
                    />
                </label>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={add.isPending}
                    onClick={() => add.mutate()}
                >
                    Adicionar medida
                </Button>
            </div>
            {error ? <p role="alert">{error}</p> : null}
        </section>
    );
}
