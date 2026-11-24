import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { ApiProblemError } from '@/lib/api';
import { AccessEndedError } from '@/lib/clientApi';
import { messageOf } from '@/features/clients/messages';
import { useClientApi } from '@/features/clients/useClientApi';

import {
    CIRCUMFERENCES,
    EMPTY_FORM,
    SKINFOLDS,
    toRequest,
    validate,
    type AssessmentForm,
} from './assessmentForm';

function todayIso(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Avaliação presencial do cliente vinculado (DEV-0049, FR-14, FR-16): peso, circunferências, dobras e a
 * bioimpedância, com a validação da API espelhada. O % de gordura é calculado pela API.
 */
export function AssessmentFormPage({ now = new Date() }: { now?: Date }) {
    const { clientId = '' } = useParams();
    const api = useClientApi();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [form, setForm] = useState<AssessmentForm>(EMPTY_FORM);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();

    const save = useMutation({
        mutationFn: () => api.recordPresential(clientId, toRequest(form)),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: ['professional', 'client', clientId, 'assessments'],
            });
            void navigate(`/app/clientes/${clientId}`);
        },
        onError: (error) => {
            if (error instanceof ApiProblemError && Object.keys(error.fieldErrors).length > 0) {
                setErrors(error.fieldErrors);
                return;
            }
            setFormError(messageOf(error));
        },
    });

    if (save.error instanceof AccessEndedError) {
        return (
            <section className="space-y-3">
                <p role="alert">{save.error.message}</p>
                <Link className="underline" to="/app">
                    Voltar para os clientes
                </Link>
            </section>
        );
    }

    const change = (field: string, value: string) => {
        setForm((current) => ({ ...current, [field]: value }));
        setErrors((current) => {
            const next = { ...current };
            delete next[field];
            return next;
        });
    };

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (save.isPending) return;
        setFormError(undefined);
        const found = validate(form, todayIso(now));
        setErrors(found);
        if (Object.keys(found).length === 0) save.mutate();
    };

    const field = (name: string, label: string, errorKey: string, type = 'text') => (
        <label className="block space-y-1">
            <span className="text-sm font-medium">{label}</span>
            <input
                type={type}
                inputMode={type === 'text' ? 'decimal' : undefined}
                className="block w-full rounded border px-2 py-1"
                value={form[name] ?? ''}
                onChange={(event) => change(name, event.target.value)}
                aria-invalid={errors[errorKey] ? true : undefined}
                aria-describedby={errors[errorKey] ? `${name}-erro` : undefined}
            />
            {errors[errorKey] ? (
                <span id={`${name}-erro`} className="text-sm text-red-700">
                    {errors[errorKey]}
                </span>
            ) : null}
        </label>
    );

    return (
        <section className="max-w-2xl space-y-4">
            <Link className="underline" to={`/app/clientes/${clientId}`}>
                Voltar para o cliente
            </Link>
            <h3 className="text-base font-semibold">Nova avaliação presencial</h3>
            <form noValidate onSubmit={submit} className="space-y-4">
                <div className="grid gap-3 sm:grid-cols-3">
                    {field('measuredOn', 'Data da medição', 'measuredOn', 'date')}
                    {field('weightKg', 'Peso (kg)', 'weightKg')}
                    <label className="block space-y-1">
                        <span className="text-sm font-medium">Sexo para as fórmulas</span>
                        <select
                            className="block w-full rounded border px-2 py-1"
                            value={form.sexForFormulas}
                            onChange={(event) => change('sexForFormulas', event.target.value)}
                        >
                            <option value="">Não calcular</option>
                            <option value="FEMALE">Feminino</option>
                            <option value="MALE">Masculino</option>
                        </select>
                    </label>
                </div>
                <fieldset className="space-y-2">
                    <legend className="font-medium">Circunferências (cm)</legend>
                    <div className="grid gap-3 sm:grid-cols-3">
                        {CIRCUMFERENCES.map(([key, label]) => (
                            <div key={key}>{field(`c.${key}`, label, `circumferences.${key}`)}</div>
                        ))}
                    </div>
                </fieldset>
                <fieldset className="space-y-2">
                    <legend className="font-medium">Dobras cutâneas (mm)</legend>
                    <div className="grid gap-3 sm:grid-cols-3">
                        {SKINFOLDS.map(([key, label]) => (
                            <div key={key}>
                                {field(`s.${key}`, `Dobra ${label.toLowerCase()}`, `skinfolds.${key}`)}
                            </div>
                        ))}
                    </div>
                </fieldset>
                {field(
                    'bioimpedance',
                    '% de gordura da bioimpedância (opcional)',
                    'bioimpedanceBodyFatPercent',
                )}
                {formError ? <p role="alert">{formError}</p> : null}
                <Button type="submit" disabled={save.isPending}>
                    {save.isPending ? 'Salvando…' : 'Salvar avaliação'}
                </Button>
            </form>
        </section>
    );
}
