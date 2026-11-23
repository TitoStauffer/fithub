import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { ClientProfile } from '../../lib/api';
import { useSession } from '../auth/SessionProvider';
import { HealthFormScreen } from './HealthFormScreen';
import { messageOf, profileApiFrom, type ProfileApi } from './profileApi';
import { fromProfile } from './profileForm';
import { ProfileFormScreen } from './ProfileFormScreen';
import { Alert, Button } from './ui';

type Screen = 'home' | 'editProfile' | 'health';

/**
 * Área do cliente depois do login (DEV-0041): sem perfil, vai para o cadastro; com perfil, para a tela inicial,
 * de onde edita o perfil e o perfil de saúde.
 */
export function ClientArea({ api: injected }: { api?: ProfileApi }) {
    const { apiFetch } = useSession();
    const api = useMemo(() => injected ?? profileApiFrom(apiFetch), [injected, apiFetch]);
    const [profile, setProfile] = useState<ClientProfile | null>();
    const [loadError, setLoadError] = useState<string>();
    const [screen, setScreen] = useState<Screen>('home');
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        api.fetchProfile().then(
            (found) => active && setProfile(found),
            (error) => active && setLoadError(messageOf(error)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt]);

    if (loadError) {
        return (
            <View style={styles.box}>
                <Alert>{loadError}</Alert>
                <Button
                    onPress={() => {
                        setLoadError(undefined);
                        setAttempt((current) => current + 1);
                    }}
                >
                    Tentar de novo
                </Button>
            </View>
        );
    }
    if (profile === undefined) return <Text accessibilityLiveRegion="polite">Carregando seu perfil…</Text>;

    const saved = (updated: ClientProfile) => {
        setProfile(updated);
        setScreen('home');
    };

    if (profile === null) return <ProfileFormScreen api={api} onSaved={saved} />;
    if (screen === 'editProfile') {
        return (
            <ProfileFormScreen
                api={api}
                initial={fromProfile(profile)}
                onSaved={saved}
                onCancel={() => setScreen('home')}
            />
        );
    }
    if (screen === 'health') return <HealthFormScreen api={api} onDone={() => setScreen('home')} />;

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Olá, {profile.name}
            </Text>
            <Text>{profile.age} anos</Text>
            <Button onPress={() => setScreen('editProfile')}>Editar perfil</Button>
            <Button onPress={() => setScreen('health')}>Perfil de saúde</Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'center', alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
});
