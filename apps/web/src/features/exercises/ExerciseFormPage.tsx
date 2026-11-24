import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { messageOf } from '@/features/clients/messages';
import { ApiProblemError } from '@/lib/api';
import type { Equipment, Exercise, MuscleGroup } from '@/lib/trainingApi';

import {
    EMPTY_FORM,
    EQUIPMENT,
    LIMITS,
    MUSCLE_GROUPS,
    formOf,
    toInput,
    validate,
    type ExerciseForm,
} from './exerciseForm';
import { EXERCISES_KEY, useTrainingApi } from './useTrainingApi';

/** Cadastro (`/app/exercicios/novo`) ou edição (`/app/exercicios/:exerciseId/editar`) do exercício próprio. */
export function ExerciseFormPage() {
    const { exerciseId } = useParams();
    const api = useTrainingApi();
    const existing = useQuery({
        queryKey: [...EXERCISES_KEY, 'one', exerciseId],
        queryFn: () => api.exercise(exerciseId ?? ''),
        enabled: Boolean(exerciseId),
    });

    if (!exerciseId) return <ExerciseFormView />;
    if (existing.isPending) return <p role="status">Carregando exercício…</p>;
    if (existing.isError) {
        return (
            <section className="space-y-3">
                <p role="alert">{messageOf(existing.error)}</p>
                <Link className="underline" to="/app/exercicios">
                    Voltar para os exercícios
                </Link>
            </section>
        );
    }
    return <ExerciseFormView exercise={existing.data} />;
}

/**
 * Formulário do exercício próprio (DEV-0057), com a validação do training-service espelhada (DEV-0053); o erro que a
 * API devolver para um campo aparece no campo.
 */
function ExerciseFormView({ exercise }: { exercise?: Exercise }) {
    const api = useTrainingApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [form, setForm] = useState<ExerciseForm>(exercise ? formOf(exercise) : EMPTY_FORM);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();

    const save = useMutation({
        mutationFn: () =>
            exercise
                ? api.updateExercise(exercise.id ?? '', toInput(form))
                : api.createExercise(toInput(form)),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: EXERCISES_KEY });
            void navigate('/app/exercicios');
        },
        onError: (error) => {
            if (error instanceof ApiProblemError && Object.keys(error.fieldErrors).length > 0) {
                setErrors(error.fieldErrors);
                return;
            }
            setFormError(messageOf(error));
        },
    });

    const clearError = (field: string) =>
        setErrors((current) => {
            const next = { ...current };
            delete next[field];
            return next;
        });

    const change = <K extends keyof ExerciseForm>(field: K, value: ExerciseForm[K]) => {
        setForm((current) => ({ ...current, [field]: value }));
        clearError(field);
    };

    const toggleGroup = (group: MuscleGroup, checked: boolean) =>
        change(
            'muscleGroups',
            checked
                ? [...form.muscleGroups, group]
                : form.muscleGroups.filter((current) => current !== group),
        );

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (save.isPending) return;
        setFormError(undefined);
        const found = validate(form);
        setErrors(found);
        if (Object.keys(found).length === 0) save.mutate();
    };

    const errorOf = (field: string) =>
        errors[field] ? (
            <span id={`${field}-erro`} className="text-sm text-red-700">
                {errors[field]}
            </span>
        ) : null;
    const describedBy = (field: string) =>
        errors[field] ? { 'aria-invalid': true, 'aria-describedby': `${field}-erro` } : {};

    return (
        <section className="max-w-2xl space-y-4">
            <Link className="underline" to="/app/exercicios">
                Voltar para os exercícios
            </Link>
            <h3 className="text-base font-semibold">{exercise ? 'Editar exercício' : 'Novo exercício'}</h3>
            <form noValidate onSubmit={submit} className="space-y-4">
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Nome</span>
                    <input
                        className="block w-full rounded border px-2 py-1"
                        maxLength={LIMITS.maxName}
                        value={form.name}
                        onChange={(event) => change('name', event.target.value)}
                        {...describedBy('name')}
                    />
                </label>
                {errorOf('name')}
                <fieldset className="space-y-2" {...describedBy('muscleGroups')}>
                    <legend className="text-sm font-medium">Grupos musculares</legend>
                    <div className="grid gap-1 sm:grid-cols-3">
                        {MUSCLE_GROUPS.map(([value, label]) => (
                            <label key={value} className="flex items-center gap-2 text-sm">
                                <input
                                    type="checkbox"
                                    checked={form.muscleGroups.includes(value)}
                                    onChange={(event) => toggleGroup(value, event.target.checked)}
                                />
                                {label}
                            </label>
                        ))}
                    </div>
                    {errorOf('muscleGroups')}
                </fieldset>
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Equipamento</span>
                    <select
                        className="block w-full rounded border px-2 py-1"
                        value={form.equipment}
                        onChange={(event) => change('equipment', event.target.value as Equipment | '')}
                        {...describedBy('equipment')}
                    >
                        <option value="">Escolha</option>
                        {EQUIPMENT.map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
                {errorOf('equipment')}
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Instruções (opcional)</span>
                    <textarea
                        className="block w-full rounded border px-2 py-1"
                        rows={5}
                        value={form.instructions}
                        onChange={(event) => change('instructions', event.target.value)}
                        {...describedBy('instructions')}
                    />
                </label>
                {errorOf('instructions')}
                <label className="block space-y-1">
                    <span className="text-sm font-medium">Link do vídeo ou da imagem (opcional)</span>
                    <input
                        type="url"
                        className="block w-full rounded border px-2 py-1"
                        placeholder="https://"
                        value={form.mediaUrl}
                        onChange={(event) => change('mediaUrl', event.target.value)}
                        {...describedBy('mediaUrl')}
                    />
                </label>
                {errorOf('mediaUrl')}
                {formError ? <p role="alert">{formError}</p> : null}
                <Button type="submit" disabled={save.isPending}>
                    {save.isPending ? 'Salvando…' : 'Salvar exercício'}
                </Button>
            </form>
        </section>
    );
}
