// Endereço do client-service; no emulador Android, localhost é o próprio aparelho (use o IP da máquina).
const DEFAULT_CLIENT_API_URL = 'http://localhost:8082';

/** Consulta o health público do client-service (só o status agregado, sem dado do cliente). */
export async function fetchClientHealth(baseUrl: string = clientApiUrl()): Promise<boolean> {
    const response = await fetch(`${baseUrl}/actuator/health`);
    if (!response.ok) return false;
    const body = (await response.json()) as { status?: unknown };
    return body.status === 'UP';
}

function clientApiUrl(): string {
    const configured = process.env.EXPO_PUBLIC_CLIENT_API_URL;
    return configured && configured.length > 0 ? configured : DEFAULT_CLIENT_API_URL;
}
