import { Link, Route, Routes } from 'react-router';

import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/authContext';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { ServiceStatus } from '@/features/status/ServiceStatus';

/** Papéis que entram na área do profissional (ADR-007). */
const PROFESSIONAL_ROLES = ['personal', 'nutritionist'] as const;

/** App do profissional: página inicial pública e a área protegida por papel (DEV-0027). */
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
                            <ProtectedRoute roles={PROFESSIONAL_ROLES}>
                                <ProfessionalArea />
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

function ProfessionalArea() {
    return (
        <section className="space-y-4">
            <h2 className="text-lg font-semibold">Área do profissional</h2>
            <p className="text-muted-foreground">Em construção.</p>
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
