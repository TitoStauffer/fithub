import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { Button } from '@/components/ui/button';
import { ApiProblemError } from '@/lib/api';
import type { CreatedInvitation } from '@/lib/clientApi';

import { INVITATION_STATES, messageOf } from './messages';
import { INVITATIONS_KEY, INVITE_CODE_KEY, useClientApi } from './useClientApi';

/**
 * Convites do profissional (DEV-0043, FR-10): por e-mail ou gerando link e código para compartilhar; lista e
 * cancela os pendentes; mostra e troca o código pessoal.
 */
export function InvitationsPage() {
    const api = useClientApi();
    const queryClient = useQueryClient();
    const [email, setEmail] = useState('');
    const [emailError, setEmailError] = useState<string>();
    const [created, setCreated] = useState<CreatedInvitation>();

    const invitations = useQuery({ queryKey: INVITATIONS_KEY, queryFn: () => api.listInvitations() });
    const code = useQuery({ queryKey: INVITE_CODE_KEY, queryFn: () => api.inviteCode() });

    const create = useMutation({
        mutationFn: (to: string | undefined) => api.createInvitation(to),
        onSuccess: async (invitation) => {
            setCreated(invitation);
            setEmail('');
            await queryClient.invalidateQueries({ queryKey: INVITATIONS_KEY });
        },
        onError: (error) => {
            if (error instanceof ApiProblemError && error.fieldErrors.email)
                setEmailError(error.fieldErrors.email);
        },
    });
    const cancel = useMutation({
        mutationFn: (id: string) => api.cancelInvitation(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: INVITATIONS_KEY }),
    });
    const rotate = useMutation({
        mutationFn: () => api.rotateInviteCode(),
        onSuccess: (next) => queryClient.setQueryData(INVITE_CODE_KEY, next),
    });

    const submit = (event: FormEvent, withEmail: boolean) => {
        event.preventDefault();
        if (create.isPending) return;
        setEmailError(undefined);
        setCreated(undefined);
        if (withEmail && !/^\S+@\S+\.\S+$/.test(email.trim())) {
            setEmailError('Informe um e-mail válido.');
            return;
        }
        create.mutate(withEmail ? email.trim() : undefined);
    };

    const pending = invitations.data?.filter((invitation) => invitation.state === 'PENDING') ?? [];

    return (
        <section className="max-w-xl space-y-6">
            <div className="space-y-3">
                <h3 className="text-base font-semibold">Convidar cliente</h3>
                <form noValidate onSubmit={(event) => submit(event, true)} className="space-y-2">
                    <label className="block space-y-1">
                        <span className="font-medium">E-mail do cliente</span>
                        <input
                            type="email"
                            className="block w-full rounded border px-2 py-1"
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                            aria-invalid={emailError ? true : undefined}
                            aria-describedby={emailError ? 'email-erro' : undefined}
                        />
                    </label>
                    {emailError ? (
                        <p id="email-erro" className="text-sm text-red-700">
                            {emailError}
                        </p>
                    ) : null}
                    <div className="flex gap-2">
                        <Button type="submit" disabled={create.isPending}>
                            Convidar por e-mail
                        </Button>
                        <Button
                            type="button"
                            variant="outline"
                            disabled={create.isPending}
                            onClick={(event) => submit(event, false)}
                        >
                            Gerar link e código
                        </Button>
                    </div>
                </form>
                {create.isError && !emailError ? <p role="alert">{messageOf(create.error)}</p> : null}
                {created ? (
                    <div role="status" className="space-y-1 rounded border p-3">
                        {created.email ? (
                            <p>
                                {created.emailSent
                                    ? `Convite enviado para ${created.email}.`
                                    : `Não foi possível enviar o e-mail para ${created.email}; compartilhe o link ou o código.`}
                            </p>
                        ) : (
                            <p>Compartilhe o link ou o código com o cliente.</p>
                        )}
                        <p>
                            Link: <code>{created.link}</code>
                        </p>
                        <p>
                            Código: <code>{created.code}</code>
                        </p>
                        <p className="text-sm text-muted-foreground">
                            Vale até{' '}
                            {created.expiresAt ? new Date(created.expiresAt).toLocaleDateString('pt-BR') : ''}{' '}
                            e só pode ser usado uma vez. O link e o código não aparecem de novo.
                        </p>
                    </div>
                ) : null}
            </div>

            <div className="space-y-2">
                <h3 className="text-base font-semibold">Convites pendentes</h3>
                {invitations.isError ? <p role="alert">{messageOf(invitations.error)}</p> : null}
                {invitations.data && pending.length === 0 ? (
                    <p className="text-muted-foreground">Nenhum convite pendente.</p>
                ) : null}
                <ul className="space-y-2">
                    {pending.map((invitation) => (
                        <li key={invitation.id} className="flex items-center gap-3">
                            <span>{invitation.email ?? 'Convite por link ou código'}</span>
                            <span className="text-sm text-muted-foreground">
                                {INVITATION_STATES[invitation.state ?? ''] ?? ''}
                            </span>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={cancel.isPending}
                                onClick={() => cancel.mutate(invitation.id ?? '')}
                                aria-label={`Cancelar convite ${invitation.email ?? 'por link ou código'}`}
                            >
                                Cancelar
                            </Button>
                        </li>
                    ))}
                </ul>
                {cancel.isError ? <p role="alert">{messageOf(cancel.error)}</p> : null}
            </div>

            <div className="space-y-2">
                <h3 className="text-base font-semibold">Seu código pessoal</h3>
                <p className="text-sm text-muted-foreground">
                    O cliente informa este código no app para pedir o vínculo. Ao trocar, o código antigo
                    deixa de valer.
                </p>
                {code.data ? (
                    <p>
                        <code aria-label="Código pessoal">{code.data}</code>
                    </p>
                ) : null}
                {code.isError ? <p role="alert">{messageOf(code.error)}</p> : null}
                <Button variant="outline" disabled={rotate.isPending} onClick={() => rotate.mutate()}>
                    Trocar código
                </Button>
            </div>
        </section>
    );
}
