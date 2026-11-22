import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View } from 'react-native';

import { ServiceStatus } from './src/features/status/ServiceStatus';

/** Esqueleto do app do cliente (DEV-0021): as telas chegam com as histórias do E1 em diante. */
export default function App() {
    return (
        <View style={styles.container}>
            <Text accessibilityRole="header" style={styles.title}>
                FitHub
            </Text>
            <Text style={styles.subtitle}>Seus treinos, refeições e avaliações, em construção.</Text>
            <ServiceStatus />
            <StatusBar style="dark" />
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12, padding: 24, backgroundColor: '#fff' },
    title: { fontSize: 24, fontWeight: '600' },
    subtitle: { color: '#555', textAlign: 'center' },
});
