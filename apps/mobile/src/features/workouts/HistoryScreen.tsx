import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { SessionSummary, SessionView } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button } from '../profile/ui';
import { FEELING_LABELS } from './sessionForm';
import { formatKg, type HistoryApi, type SessionApi } from './workoutsApi';

/** Data e hora ISO como dd/mm/aaaa, no fuso do aparelho. */
export function formatDay(iso: string | undefined): string {
    if (!iso) return '';
    const date = new Date(iso);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

function divisionTitle(letter: string | undefined, name: string | null | undefined): string {
    return name ? `Treino ${letter ?? ''} — ${name}` : `Treino ${letter ?? ''}`;
}

/**
 * Histórico de treinos (DEV-0072, FR-58): os treinos finalizados, do mais novo para o mais antigo, em páginas de 20;
 * o detalhe mostra exercício por exercício com as séries, o comentário e a sensação. O histórico é do aluno e
 * continua depois que o vínculo acaba (BR-06).
 */
export function HistoryScreen({
    api,
    sessionApi,
    onDone,
}: {
    api: HistoryApi;
    sessionApi: SessionApi;
    onDone: () => void;
}) {
    const [items, setItems] = useState<SessionSummary[]>();
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [error, setError] = useState<string>();
    const [loadingMore, setLoadingMore] = useState(false);
    const [attempt, setAttempt] = useState(0);
    const [opened, setOpened] = useState<string>();

    useEffect(() => {
        let active = true;
        api.history(0).then(
            (found) => {
                if (!active) return;
                setItems(found.items ?? []);
                setTotal(found.total ?? 0);
                setPage(0);
            },
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt]);

    if (opened)
        return <SessionDetail api={sessionApi} sessionId={opened} onBack={() => setOpened(undefined)} />;

    const more = async () => {
        setLoadingMore(true);
        setError(undefined);
        try {
            const found = await api.history(page + 1);
            setItems((current) => [...(current ?? []), ...(found.items ?? [])]);
            setTotal(found.total ?? 0);
            setPage(page + 1);
        } catch (failure) {
            setError(messageOf(failure));
        } finally {
            setLoadingMore(false);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Histórico de treinos
            </Text>
            {error ? (
                <>
                    <Alert>{error}</Alert>
                    {items === undefined ? (
                        <Button
                            onPress={() => {
                                setError(undefined);
                                setAttempt((current) => current + 1);
                            }}
                        >
                            Tentar de novo
                        </Button>
                    ) : null}
                </>
            ) : null}
            {items === undefined && !error ? (
                <Text accessibilityLiveRegion="polite">Carregando o histórico…</Text>
            ) : null}
            {items?.length === 0 ? <Text>Você ainda não finalizou nenhum treino.</Text> : null}
            {items?.map((session) => (
                <Pressable
                    key={session.id}
                    accessibilityRole="button"
                    accessibilityLabel={`${formatDay(session.startedAt)}, ${session.planName ?? ''}, ${divisionTitle(
                        session.divisionLetter,
                        session.divisionName,
                    )}`}
                    onPress={() => setOpened(session.id)}
                    style={styles.card}
                >
                    <Text style={styles.strong}>{formatDay(session.startedAt)}</Text>
                    <Text>
                        {session.planName} · {divisionTitle(session.divisionLetter, session.divisionName)}
                    </Text>
                    <Text>
                        {session.setCount === 1 ? '1 série' : `${session.setCount ?? 0} séries`}
                        {session.feeling ? ` · ${FEELING_LABELS[String(session.feeling)] ?? ''}` : ''}
                    </Text>
                </Pressable>
            ))}
            {items && items.length < total ? (
                <Button disabled={loadingMore} onPress={() => void more()}>
                    {loadingMore ? 'Carregando…' : 'Carregar mais'}
                </Button>
            ) : null}
            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

/** Um treino feito, só para ler: cada exercício com as séries, o comentário e a sensação. */
function SessionDetail({
    api,
    sessionId,
    onBack,
}: {
    api: SessionApi;
    sessionId: string;
    onBack: () => void;
}) {
    const [session, setSession] = useState<SessionView>();
    const [error, setError] = useState<string>();

    useEffect(() => {
        let active = true;
        api.view(sessionId).then(
            (found) => active && setSession(found),
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, sessionId]);

    if (!session) {
        return (
            <View style={styles.box}>
                {error ? (
                    <Alert>{error}</Alert>
                ) : (
                    <Text accessibilityLiveRegion="polite">Carregando o treino…</Text>
                )}
                <Button onPress={onBack}>Voltar</Button>
            </View>
        );
    }

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                {divisionTitle(session.divisionLetter, session.divisionName)}
            </Text>
            <Text>{formatDay(session.startedAt)}</Text>
            {(session.items ?? []).map((item) => {
                const names = new Map(
                    [item.exercise, ...(item.alternatives ?? [])].map((each) => [each?.id, each?.name]),
                );
                return (
                    <View key={item.position} style={styles.card}>
                        <Text style={styles.strong}>{item.exercise?.name}</Text>
                        {(item.recorded ?? []).length === 0 ? <Text>Nenhuma série registrada.</Text> : null}
                        {(item.recorded ?? []).map((set) => (
                            <Text key={set.id}>
                                Série {set.setNumber}: {formatKg(set.loadKg ?? 0)} × {set.reps}
                                {set.exerciseId && set.exerciseId !== item.exercise?.id
                                    ? ` (${names.get(set.exerciseId) ?? 'alternativa'})`
                                    : ''}
                            </Text>
                        ))}
                    </View>
                );
            })}
            {session.comment ? <Text>Comentário: {session.comment}</Text> : null}
            {session.feeling ? <Text>Sensação: {FEELING_LABELS[String(session.feeling)]}</Text> : null}
            <Button onPress={onBack}>Voltar ao histórico</Button>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch', padding: 4 },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    card: { gap: 4, borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10 },
    strong: { fontWeight: '600' },
});
