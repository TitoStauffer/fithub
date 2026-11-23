import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router';

import { Button } from '@/components/ui/button';
import { SessionUnavailableError, useAuth } from '@/features/auth/authContext';
import { useApiFetch } from '@/features/auth/useApiFetch';
import { ApiProblemError, fetchMyProfessional, registerProfessional } from '@/lib/api';
import { config } from '@/lib/config';

import { EMPTY_FORM, STATES, toRequest, validate, type RegistrationForm } from './registrationForm';

const PROFESSIONAL_ROLES = ['personal', 'nutritionist'] as const;
const MY_PROFESSIONAL = ['professional', 'me'] as const;

function messageOf(error: unknown): string {
    if (error instanceof SessionUnavailableError) return error.message;
    if (error instanceof ApiProblemError) return error.message;
    return 'Não foi possível concluir. Tente de novo.';
}

/**
 * Onboarding do profissional (DEV-0031): quem entrou mas ainda não tem cadastro de profissional informa o
 * tipo e o registro. Depois do cadastro, a sessão é renovada para o token trazer o papel novo, e a
 * pessoa entra na área do profissional sem sair e entrar de novo. A tela não libera nada sozinha: quem
 * decide é o papel no token, conferido pela API.
 */
export function OnboardingPage() {
    const { user, reloadSession } = useAuth();
    const apiFetch = useApiFetch();
    const queryClient = useQueryClient();
    const [form, setForm] = useState<RegistrationForm>(EMPTY_FORM);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string>();
    const [reloadError, setReloadError] = useState<string>();

    const mine = useQuery({
        queryKey: MY_PROFESSIONAL,
        queryFn: () => fetchMyProfessional(apiFetch, config.accountApiUrl),
        retry: false,
    });

    const enterArea = async () => {
        setReloadError(undefined);
        try {
            await reloadSession();
        } catch (error) {
            setReloadError(messageOf(error));
        }
    };

    const register = useMutation({
        mutationFn: () => registerProfessional(apiFetch, config.accountApiUrl, toRequest(form)),
        onSuccess: async (professional) => {
            queryClient.setQueryData(MY_PROFESSIONAL, professional);
            await enterArea();
        },
        onError: (error) => {
            if (error instanceof ApiProblemError && Object.keys(error.fieldErrors).length > 0) {
                setErrors(error.fieldErrors);
                return;
            }
            setFormError(messageOf(error));
        },
    });

    if (user && PROFESSIONAL_ROLES.some((role) => user.roles.includes(role))) {
        return <Navigate to="/app" replace />;
    }

    if (mine.isPending) return <p role="status">Carregando…</p>;

    if (mine.isError) {
        return (
            <section role="alert" className="space-y-3">
                <p>{messageOf(mine.error)}</p>
                <Button variant="outline" onClick={() => void mine.refetch()}>
                    Tentar de novo
                </Button>
            </section>
        );
    }

    if (mine.data) {
        // Cadastro feito, mas o token ainda não traz o papel (atribuição pendente no Keycloak).
        return (
            <section className="max-w-lg space-y-3">
                <h2 className="text-lg font-semibold">Cadastro recebido</h2>
                <p className="text-muted-foreground">
                    Seu cadastro como profissional ({mine.data.registration?.display}) está pronto. Estamos
                    liberando seu acesso; isso costuma levar alguns segundos.
                </p>
                {reloadError && (
                    <p role="alert" className="text-sm text-red-700">
                        {reloadError}
                    </p>
                )}
                <Button onClick={() => void enterArea()}>Entrar na área do profissional</Button>
            </section>
        );
    }

    const change = (field: keyof RegistrationForm, value: string) => {
        setForm((current) => ({ ...current, [field]: value }));
        setErrors((current) => {
            const next = { ...current };
            delete next[field];
            return next;
        });
    };

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (register.isPending) return;
        setFormError(undefined);
        const found = validate(form);
        setErrors(found);
        if (Object.keys(found).length === 0) register.mutate();
    };

    const fieldProps = (field: string) => ({
        'aria-invalid': errors[field] ? true : undefined,
        'aria-describedby': errors[field] ? `${field}-erro` : undefined,
    });
    const fieldError = (field: string) =>
        errors[field] ? (
            <p id={`${field}-erro`} className="text-sm text-red-700">
                {errors[field]}
            </p>
        ) : null;

    return (
        <section className="max-w-lg space-y-4">
            <h2 className="text-lg font-semibold">Complete seu cadastro de profissional</h2>
            <p className="text-muted-foreground">
                Informe seu registro no conselho. No momento, o registro é declarado por você e não é
                consultado no conselho.
            </p>
            <form noValidate onSubmit={submit} className="space-y-4">
                <fieldset className="space-y-2" {...fieldProps('type')}>
                    <legend className="font-medium">Você é</legend>
                    {(
                        [
                            ['PERSONAL', 'Personal trainer (CREF)'],
                            ['NUTRITIONIST', 'Nutricionista (CRN)'],
                        ] as const
                    ).map(([value, label]) => (
                        <label key={value} className="flex items-center gap-2">
                            <input
                                type="radio"
                                name="type"
                                value={value}
                                checked={form.type === value}
                                onChange={() => change('type', value)}
                            />
                            {label}
                        </label>
                    ))}
                    {fieldError('type')}
                </fieldset>

                <div className="space-y-1">
                    <label htmlFor="registrationNumber" className="font-medium">
                        Número do registro
                    </label>
                    <input
                        id="registrationNumber"
                        inputMode="numeric"
                        maxLength={6}
                        className="block w-full rounded-md border px-3 py-2"
                        value={form.registrationNumber}
                        onChange={(event) => change('registrationNumber', event.target.value)}
                        {...fieldProps('registrationNumber')}
                    />
                    {fieldError('registrationNumber')}
                </div>

                {form.type === 'PERSONAL' && (
                    <div className="space-y-1">
                        <label htmlFor="registrationCategory" className="font-medium">
                            Categoria do CREF
                        </label>
                        <select
                            id="registrationCategory"
                            className="block w-full rounded-md border px-3 py-2"
                            value={form.registrationCategory}
                            onChange={(event) => change('registrationCategory', event.target.value)}
                            {...fieldProps('registrationCategory')}
                        >
                            <option value="">Selecione</option>
                            <option value="G">G — graduado</option>
                            <option value="P">P — provisionado</option>
                        </select>
                        {fieldError('registrationCategory')}
                    </div>
                )}

                <div className="space-y-1">
                    <label htmlFor="registrationState" className="font-medium">
                        UF do registro
                    </label>
                    <select
                        id="registrationState"
                        className="block w-full rounded-md border px-3 py-2"
                        value={form.registrationState}
                        onChange={(event) => change('registrationState', event.target.value)}
                        {...fieldProps('registrationState')}
                    >
                        <option value="">Selecione</option>
                        {STATES.map((state) => (
                            <option key={state} value={state}>
                                {state}
                            </option>
                        ))}
                    </select>
                    {fieldError('registrationState')}
                </div>

                {formError && (
                    <p role="alert" className="text-sm text-red-700">
                        {formError}
                    </p>
                )}

                <Button type="submit" disabled={register.isPending}>
                    {register.isPending ? 'Enviando…' : 'Concluir cadastro'}
                </Button>
            </form>
        </section>
    );
}
