import { ServiceStatus } from '@/features/status/ServiceStatus';

/** Esqueleto do app do profissional (DEV-0020): as telas chegam com as histórias do E1 em diante. */
export function App() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <header className="border-b px-6 py-4">
                <h1 className="text-xl font-semibold">FitHub — Profissional</h1>
            </header>
            <main className="space-y-4 px-6 py-8">
                <p className="text-muted-foreground">Área do personal e da nutricionista, em construção.</p>
                <ServiceStatus />
            </main>
        </div>
    );
}
