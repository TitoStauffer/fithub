import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useSession } from './SessionProvider';

/** Tela de entrada (DEV-0028): abre o login do Keycloak no navegador do sistema. */
export function SignInScreen() {
    const { session } = useSession();
    const [busy, setBusy] = useState(false);
    const [failed, setFailed] = useState(false);

    const signIn = () => {
        setBusy(true);
        setFailed(false);
        session
            .signIn()
            .catch(() => setFailed(true))
            .finally(() => setBusy(false));
    };

    return (
        <View style={styles.box}>
            <Text style={styles.subtitle}>Entre para ver seus treinos, refeições e avaliações.</Text>
            {failed && (
                <Text accessibilityRole="alert" style={styles.error}>
                    Não foi possível entrar. Tente de novo.
                </Text>
            )}
            <Pressable
                accessibilityRole="button"
                accessibilityState={{ disabled: busy }}
                disabled={busy}
                onPress={signIn}
                style={[styles.button, busy && styles.buttonBusy]}
            >
                <Text style={styles.buttonText}>{busy ? 'Abrindo o login…' : 'Entrar'}</Text>
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { alignItems: 'center', gap: 12 },
    subtitle: { color: '#555', textAlign: 'center' },
    error: { color: '#b42318', textAlign: 'center' },
    button: { backgroundColor: '#171717', borderRadius: 10, paddingHorizontal: 24, paddingVertical: 12 },
    buttonBusy: { opacity: 0.6 },
    buttonText: { color: '#fafafa', fontWeight: '600' },
});
