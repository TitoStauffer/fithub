import { useEffect, useMemo, useState } from 'react';
import { Linking, StyleSheet, Text, View } from 'react-native';

import type { ClientProfile } from '../../lib/api';
import { assessmentApiFrom, type AssessmentApi, type PhotoSource } from '../assessments/assessmentApi';
import { expoPhotoSource } from '../assessments/expoPhotoSource';
import { OnlineAssessmentScreen } from '../assessments/OnlineAssessmentScreen';
import { PhotoComparisonScreen } from '../assessments/PhotoComparisonScreen';
import { useSession } from '../auth/SessionProvider';
import { AcceptInvitationScreen } from '../links/AcceptInvitationScreen';
import { linksApiFrom, tokenFromUrl, type LinksApi } from '../links/linksApi';
import { LinksScreen } from '../links/LinksScreen';
import { TodayWorkoutScreen } from '../workouts/TodayWorkoutScreen';
import { sessionApiFrom, workoutsApiFrom, type SessionApi, type WorkoutsApi } from '../workouts/workoutsApi';
import { WorkoutSessionScreen } from '../workouts/WorkoutSessionScreen';
import { HealthFormScreen } from './HealthFormScreen';
import { messageOf, profileApiFrom, type ProfileApi } from './profileApi';
import { fromProfile } from './profileForm';
import { ProfileFormScreen } from './ProfileFormScreen';
import { Alert, Button } from './ui';

type Screen =
    'home' | 'editProfile' | 'health' | 'accept' | 'links' | 'assessment' | 'compare' | 'workout' | 'session';

/** De onde chegam os links abertos pelo sistema (`fithub://convite/<token>`); injetável nos testes. */
export interface LinkSource {
    getInitialURL(): Promise<string | null | undefined>;
    addEventListener(type: 'url', handler: (event: { url: string }) => void): { remove(): void };
}

/**
 * Área do cliente depois do login (DEV-0041): sem perfil, vai para o cadastro; com perfil, para a tela inicial,
 * de onde edita o perfil e o perfil de saúde.
 */
export function ClientArea({
    api: injected,
    linksApi: injectedLinks,
    linkSource = Linking,
    assessmentApi: injectedAssessments,
    photoSource = expoPhotoSource,
    workoutsApi: injectedWorkouts,
    sessionApi: injectedSessions,
}: {
    api?: ProfileApi;
    linksApi?: LinksApi;
    linkSource?: LinkSource;
    assessmentApi?: AssessmentApi;
    photoSource?: PhotoSource;
    workoutsApi?: WorkoutsApi;
    sessionApi?: SessionApi;
}) {
    const { apiFetch } = useSession();
    const api = useMemo(() => injected ?? profileApiFrom(apiFetch), [injected, apiFetch]);
    const linksApi = useMemo(() => injectedLinks ?? linksApiFrom(apiFetch), [injectedLinks, apiFetch]);
    const assessmentApi = useMemo(
        () => injectedAssessments ?? assessmentApiFrom(apiFetch),
        [injectedAssessments, apiFetch],
    );
    const workoutsApi = useMemo(
        () => injectedWorkouts ?? workoutsApiFrom(apiFetch),
        [injectedWorkouts, apiFetch],
    );
    const sessionApi = useMemo(
        () => injectedSessions ?? sessionApiFrom(apiFetch),
        [injectedSessions, apiFetch],
    );
    // Treino aberto no registro por série (DEV-0071).
    const [sessionId, setSessionId] = useState<string>();
    // Token do convite aberto pelo link (DEV-0042): vale depois do perfil, que o aceite exige.
    const [invitationToken, setInvitationToken] = useState<string>();
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

    useEffect(() => {
        const open = (url: string | null | undefined) => {
            const token = tokenFromUrl(url);
            if (token) {
                setInvitationToken(token);
                setScreen('accept');
            }
        };
        void linkSource.getInitialURL().then(open, () => undefined);
        const subscription = linkSource.addEventListener('url', (event) => open(event.url));
        return () => subscription.remove();
    }, [linkSource]);

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
    if (screen === 'accept') {
        return (
            <AcceptInvitationScreen
                api={linksApi}
                token={invitationToken}
                onDone={(accepted) => {
                    setInvitationToken(undefined);
                    setScreen(accepted ? 'links' : 'home');
                }}
            />
        );
    }
    if (screen === 'links') return <LinksScreen api={linksApi} onDone={() => setScreen('home')} />;
    if (screen === 'workout') {
        return (
            <TodayWorkoutScreen
                api={workoutsApi}
                onOpenSession={(opened) => {
                    setSessionId(opened);
                    setScreen('session');
                }}
                onDone={() => setScreen('home')}
            />
        );
    }
    if (screen === 'session' && sessionId) {
        // Ao finalizar, volta ao treino de hoje, que relê a ficha e mostra o próximo treino.
        return (
            <WorkoutSessionScreen
                api={sessionApi}
                sessionId={sessionId}
                onFinished={() => setScreen('workout')}
                onBack={() => setScreen('workout')}
            />
        );
    }
    if (screen === 'compare') {
        return <PhotoComparisonScreen api={assessmentApi} onDone={() => setScreen('home')} />;
    }
    if (screen === 'assessment') {
        return (
            <OnlineAssessmentScreen
                api={assessmentApi}
                photos={photoSource}
                onDone={() => setScreen('home')}
            />
        );
    }

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Olá, {profile.name}
            </Text>
            <Text>{profile.age} anos</Text>
            <Button onPress={() => setScreen('workout')}>Treino de hoje</Button>
            <Button onPress={() => setScreen('editProfile')}>Editar perfil</Button>
            <Button onPress={() => setScreen('health')}>Perfil de saúde</Button>
            <Button onPress={() => setScreen('accept')}>Aceitar convite</Button>
            <Button onPress={() => setScreen('links')}>Meus vínculos</Button>
            <Button onPress={() => setScreen('assessment')}>Avaliação online</Button>
            <Button onPress={() => setScreen('compare')}>Comparar fotos</Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'center', alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
});
