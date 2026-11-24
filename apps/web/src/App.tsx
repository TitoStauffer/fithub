import type { ReactNode } from 'react';
import { Link, Navigate, NavLink, Route, Routes } from 'react-router';

import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/authContext';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { AssessmentFormPage } from '@/features/assessments/AssessmentFormPage';
import { ClientDetailPage } from '@/features/clients/ClientDetailPage';
import { ClientsPage } from '@/features/clients/ClientsPage';
import { InvitationsPage } from '@/features/clients/InvitationsPage';
import { ExerciseFormPage } from '@/features/exercises/ExerciseFormPage';
import { ExercisesPage } from '@/features/exercises/ExercisesPage';
import { PlanEditorPage } from '@/features/plans/PlanEditorPage';
import { OnboardingPage } from '@/features/onboarding/OnboardingPage';
import { ServiceStatus } from '@/features/status/ServiceStatus';

/** Papéis que entram na área do profissional (ADR-007). */
const PROFESSIONAL_ROLES = ['personal', 'nutritionist'] as const;

/**
 * App do profissional: página inicial pública, a área protegida por papel (DEV-0027) e o onboarding de
 * quem ainda não tem cadastro de profissional (DEV-0031).
 */
export function App() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <Header />
            <main className="space-y-4 px-6 py-8">
                <Routes>
                    <Route path="/" element={<HomePage />} />
                    <Route
                        path="/app/*"
                        element={
                            <ProtectedRoute
                                roles={PROFESSIONAL_ROLES}
                                onMissingRole={<Navigate to="/onboarding" replace />}
                            >
                                <ProfessionalArea />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/onboarding"
                        element={
                            <ProtectedRoute>
                                <OnboardingPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route path="*" element={<NotFoundPage />} />
                </Routes>
            </main>
        </div>
    );
}

function Header() {
    const { status, user, logout } = useAuth();
    return (
        <header className="flex items-center justify-between border-b px-6 py-4">
            <h1 className="text-xl font-semibold">
                <Link to="/">FitHub — Profissional</Link>
            </h1>
            {status === 'authenticated' && user ? (
                <div className="flex items-center gap-3">
                    <span className="text-sm text-muted-foreground">{user.name || user.email}</span>
                    <Button variant="outline" size="sm" onClick={() => void logout()}>
                        Sair
                    </Button>
                </div>
            ) : null}
        </header>
    );
}

function HomePage() {
    return (
        <section className="space-y-4">
            <p className="text-muted-foreground">Área do personal e da nutricionista.</p>
            <Button asChild>
                <Link to="/app">Entrar</Link>
            </Button>
        </section>
    );
}

/** Área do profissional (DEV-0043): clientes vinculados, detalhe do cliente, convites e, para o personal, exercícios. */
function ProfessionalArea() {
    const { user } = useAuth();
    const personal = user?.roles.includes('personal') ?? false;
    const onlyPersonal = (page: ReactNode) => (
        <ProtectedRoute
            roles={['personal']}
            onMissingRole={<p role="alert">O catálogo de exercícios é só para o personal.</p>}
        >
            {page}
        </ProtectedRoute>
    );
    return (
        <section className="space-y-4">
            <h2 className="text-lg font-semibold">Área do profissional</h2>
            <nav aria-label="Área do profissional" className="flex gap-4">
                <NavLink className="underline" to="/app" end>
                    Clientes
                </NavLink>
                <NavLink className="underline" to="/app/convites">
                    Convites
                </NavLink>
                {personal ? (
                    <NavLink className="underline" to="/app/exercicios">
                        Exercícios
                    </NavLink>
                ) : null}
            </nav>
            <Routes>
                <Route index element={<ClientsPage />} />
                <Route path="convites" element={<InvitationsPage />} />
                <Route path="clientes/:clientId" element={<ClientDetailPage />} />
                <Route path="clientes/:clientId/avaliacoes/nova" element={<AssessmentFormPage />} />
                <Route path="exercicios" element={onlyPersonal(<ExercisesPage />)} />
                <Route path="exercicios/novo" element={onlyPersonal(<ExerciseFormPage />)} />
                <Route path="exercicios/:exerciseId/editar" element={onlyPersonal(<ExerciseFormPage />)} />
                <Route path="clientes/:clientId/fichas/nova" element={onlyPersonal(<PlanEditorPage />)} />
                <Route path="clientes/:clientId/fichas/:planId" element={onlyPersonal(<PlanEditorPage />)} />
            </Routes>
            <ServiceStatus />
        </section>
    );
}

function NotFoundPage() {
    return (
        <section className="space-y-3">
            <h2 className="text-lg font-semibold">Página não encontrada</h2>
            <Link className="underline" to="/">
                Voltar para o início
            </Link>
        </section>
    );
}
