import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { fetchClientHealth } from './clientHealth';

type Status = 'checking' | 'available' | 'unavailable';

type ServiceStatusProps = {
    /** Injetável nos testes; o padrão consulta o client-service. */
    checkHealth?: () => Promise<boolean>;
};

const MESSAGES: Record<Status, string> = {
    checking: 'Verificando o serviço…',
    available: 'Serviço disponível.',
    unavailable: 'Serviço indisponível no momento.',
};

/**
 * Mostra se a API do cliente responde, com os estados de carregando, sucesso e erro e uma ação para
 * tentar de novo. Exemplo do padrão de tela com dado remoto no app.
 */
export function ServiceStatus({ checkHealth = fetchClientHealth }: ServiceStatusProps) {
    const [status, setStatus] = useState<Status>('checking');
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        // O estado só muda quando a resposta chega, e nunca depois de a tela sair.
        let active = true;
        checkHealth().then(
            (available) => active && setStatus(available ? 'available' : 'unavailable'),
            () => active && setStatus('unavailable'),
        );
        return () => {
            active = false;
        };
    }, [checkHealth, attempt]);

    const retry = () => {
        setStatus('checking');
        setAttempt((current) => current + 1);
    };

    return (
        <View style={styles.row}>
            <Text accessibilityRole="text" accessibilityLiveRegion="polite">
                {MESSAGES[status]}
            </Text>
            {status === 'unavailable' && (
                <Pressable accessibilityRole="button" onPress={retry} style={styles.button}>
                    <Text>Tentar de novo</Text>
                </Pressable>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    row: { alignItems: 'center', gap: 8 },
    button: { borderWidth: 1, borderColor: '#ccc', borderRadius: 6, paddingHorizontal: 12, paddingVertical: 6 },
});
