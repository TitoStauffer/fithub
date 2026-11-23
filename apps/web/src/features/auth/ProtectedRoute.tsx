import { useEffect, useRef, type ReactNode } from 'react';

import { Button } from '@/components/ui/button';

import type { Role } from './authClient';
import { useAuth } from './authContext';

/**
 * Só mostra o conteúdo para quem tem sessão e pelo menos um dos papéis (DEV-0027). Sem sessão, leva ao
 * login uma vez. Quando o login não se conclui (`failed`), mostra o erro e espera a pessoa pedir de
 * novo, em vez de redirecionar sozinho. A proteção de verdade é da API: aqui é só o que a tela mostra.
 */
export function ProtectedRoute({
    roles,
    onMissingRole,
    children,
}: {
    /** Papéis aceitos; sem a lista, basta ter sessão. */
    roles?: readonly Role[];
    /** O que mostrar a quem tem sessão mas não tem o papel (padrão: acesso negado). */
    onMissingRole?: ReactNode;
    children: ReactNode;
}) {
    const { status, user, login, logout } = useAuth();
    const redirected = useRef(false);

    useEffect(() => {
        if (status !== 'anonymous' || redirected.current) return;
        redirected.current = true;
        void login();
    }, [status, login]);

    if (status === 'loading') return <p role="status">Carregando…</p>;

    if (status === 'failed') {
        return (
            <section role="alert" className="space-y-3">
                <h2 className="text-lg font-semibold">Não foi possível iniciar a sessão</h2>
                <p className="text-muted-foreground">O login não foi concluído. Tente entrar de novo.</p>
                <Button onClick={() => void login()}>Entrar de novo</Button>
            </section>
        );
    }

    if (status === 'rejected') {
        // Sair encerra a sessão no Keycloak; sem isso, o login devolveria a mesma sessão e o laço voltaria.
        return (
            <section role="alert" className="space-y-3">
                <h2 className="text-lg font-semibold">Sua sessão não foi aceita</h2>
                <p className="text-muted-foreground">
                    O FitHub recusou o acesso desta sessão. Saia e entre de novo; se continuar, fale com o
                    suporte.
                </p>
                <Button onClick={() => void logout()}>Sair e entrar de novo</Button>
            </section>
        );
    }

    if (status !== 'authenticated' || !user) return <p role="status">Redirecionando para o login…</p>;

    if (roles && !roles.some((role) => user.roles.includes(role))) {
        if (onMissingRole) return onMissingRole;
        return (
            <section role="alert" className="space-y-3">
                <h2 className="text-lg font-semibold">Acesso negado</h2>
                <p className="text-muted-foreground">
                    Esta área é só para profissionais (personal ou nutricionista). Sua conta ainda não tem
                    esse acesso.
                </p>
                <Button variant="outline" onClick={() => void logout()}>
                    Sair
                </Button>
            </section>
        );
    }

    return children;
}
