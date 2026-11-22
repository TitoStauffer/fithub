import { StatusBar } from 'expo-status-bar';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { createExpoAuthGateway } from './src/features/auth/expoAuthGateway';
import { secureTokenStore } from './src/features/auth/secureTokenStore';
import { createSession, type Session } from './src/features/auth/session';
import { SessionProvider, useSession } from './src/features/auth/SessionProvider';
import { SignInScreen } from './src/features/auth/SignInScreen';
import { ServiceStatus } from './src/features/status/ServiceStatus';

// Uma sessão por app: é ela que guarda o access token em memória.
const appSession = createSession(createExpoAuthGateway(), secureTokenStore);

/** App do cliente: tela de entrada sem sessão e a área do cliente com sessão (DEV-0028). */
export default function App({ session = appSession }: { session?: Session }) {
    return (
        <SessionProvider session={session}>
            <View style={styles.container}>
                <Text accessibilityRole="header" style={styles.title}>
                    FitHub
                </Text>
                <Content />
                <StatusBar style="dark" />
            </View>
        </SessionProvider>
    );
}

function Content() {
    const { status, session } = useSession();

    if (status === 'loading') return <Text accessibilityLiveRegion="polite">Carregando…</Text>;

    if (status === 'unavailable') {
        return (
            <View style={styles.box}>
                <Text accessibilityRole="alert" style={styles.subtitle}>
                    Não foi possível falar com o FitHub. Confira a conexão.
                </Text>
                <Pressable
                    accessibilityRole="button"
                    onPress={() => void session.restore()}
                    style={styles.button}
                >
                    <Text>Tentar de novo</Text>
                </Pressable>
            </View>
        );
    }

    if (status === 'signedOut') return <SignInScreen />;

    return (
        <View style={styles.box}>
            <Text style={styles.subtitle}>Seus treinos, refeições e avaliações, em construção.</Text>
            <ServiceStatus />
            <Pressable
                accessibilityRole="button"
                onPress={() => void session.signOut()}
                style={styles.button}
            >
                <Text>Sair</Text>
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: 24,
        backgroundColor: '#fff',
    },
    box: { alignItems: 'center', gap: 12 },
    title: { fontSize: 24, fontWeight: '600' },
    subtitle: { color: '#555', textAlign: 'center' },
    button: {
        borderWidth: 1,
        borderColor: '#ccc',
        borderRadius: 6,
        paddingHorizontal: 12,
        paddingVertical: 6,
    },
});
