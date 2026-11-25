import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import type { BodySeries, DietProgress, TrainingProgress } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Choices } from '../profile/ui';
import { formatDate, formatKg } from '../workouts/workoutsApi';
import {
    bodySummaries,
    formatNumber,
    PERIODS,
    periodStart,
    type PeriodWeeks,
    type ProgressApi,
} from './progressApi';

/** O estado de um bloco: carregando, com o dado ou com o erro. */
type Block<T> = { data?: T; error?: string };

/** Carrega o bloco de novo a cada mudança de `key`; o erro fica no bloco, sem derrubar os outros. */
function useBlock<T>(load: () => Promise<T>, key: string): Block<T> {
    // O resultado guarda a chave de que veio: com a chave nova, o bloco volta a carregar sem limpar o estado no efeito.
    const [block, setBlock] = useState<Block<T> & { key?: string }>({});
    useEffect(() => {
        let active = true;
        load().then(
            (data) => active && setBlock({ key, data }),
            (error) => active && setBlock({ key, error: messageOf(error) }),
        );
        return () => {
            active = false;
        };
        // `load` muda a cada render; quem decide recarregar é a chave.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key]);
    return block.key === key ? block : {};
}

/**
 * Minha evolução (DEV-0096, FR-58): treino (aderência do período e carga do exercício escolhido, com o atalho para os
 * recordes), dieta (aderência do período) e corpo (primeiro valor, último e variação de cada medida, com o % de
 * gordura por método). Sem ficha, sem plano ou sem avaliação, o bloco diz o que falta; erro de conexão deixa tentar
 * de novo.
 */
export function MyProgressScreen({
    api,
    now = new Date(),
    onRecords,
    onDone,
}: {
    api: ProgressApi;
    now?: Date;
    onRecords: () => void;
    onDone: () => void;
}) {
    const [weeks, setWeeks] = useState<PeriodWeeks>(4);
    const [attempt, setAttempt] = useState(0);
    const [exerciseId, setExerciseId] = useState<string>();
    const from = periodStart(now, weeks);

    const training = useBlock<TrainingProgress>(() => api.training(from), `${from}:${attempt}`);
    const diet = useBlock<DietProgress>(() => api.diet(from), `${from}:${attempt}`);
    const body = useBlock<BodySeries>(() => api.body(), String(attempt));
    const failed = Boolean(training.error ?? diet.error ?? body.error);

    const exercises = (training.data?.exercises ?? []).filter((item) => (item.points ?? []).length > 0);
    const exercise = exercises.find((item) => item.exerciseId === exerciseId) ?? exercises[0];
    const trainingAdherence = training.data?.adherence;
    const dietAdherence = diet.data?.adherence;
    const summaries = body.data ? bodySummaries(body.data) : undefined;

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Minha evolução
            </Text>
            <Choices
                label="Período"
                options={PERIODS.map((option) => ({ value: String(option), label: `${option} semanas` }))}
                selected={[String(weeks)]}
                onToggle={(value) => setWeeks(Number(value) as PeriodWeeks)}
            />
            {failed ? (
                <Button onPress={() => setAttempt((current) => current + 1)}>Tentar de novo</Button>
            ) : null}

            <View style={styles.card} accessibilityLabel="Treino">
                <Text style={styles.strong}>Treino</Text>
                {training.error ? <Alert>{training.error}</Alert> : null}
                {!training.data && !training.error ? (
                    <Text accessibilityLiveRegion="polite">Carregando o treino…</Text>
                ) : null}
                {trainingAdherence && trainingAdherence.percent === undefined ? (
                    <Text>Sem ficha no período. A aderência aparece com a ficha publicada.</Text>
                ) : null}
                {trainingAdherence && trainingAdherence.percent !== undefined ? (
                    <Text>
                        Aderência: {trainingAdherence.done} de {formatNumber(trainingAdherence.expected ?? 0)}{' '}
                        treinos esperados ({formatNumber(trainingAdherence.percent)}%)
                    </Text>
                ) : null}
                {training.data && exercises.length === 0 ? (
                    <Text>Nenhum treino finalizado no período.</Text>
                ) : null}
                {exercise ? (
                    <>
                        <Choices
                            label="Exercício"
                            options={exercises.map((item) => ({
                                value: item.exerciseId ?? '',
                                label: item.name ?? '',
                            }))}
                            selected={[exercise.exerciseId ?? '']}
                            onToggle={setExerciseId}
                        />
                        {(exercise.points ?? []).map((point) => (
                            <Text key={point.date}>
                                {formatDate(point.date)}:{' '}
                                {point.maxLoadKg === undefined ? 'sem carga' : formatKg(point.maxLoadKg)},
                                volume {formatKg(point.volumeKg ?? 0)}
                            </Text>
                        ))}
                    </>
                ) : null}
                <Button onPress={onRecords}>Ver recordes</Button>
            </View>

            <View style={styles.card} accessibilityLabel="Dieta">
                <Text style={styles.strong}>Dieta</Text>
                {diet.error ? <Alert>{diet.error}</Alert> : null}
                {!diet.data && !diet.error ? (
                    <Text accessibilityLiveRegion="polite">Carregando a dieta…</Text>
                ) : null}
                {dietAdherence && dietAdherence.percent === undefined ? (
                    <Text>Sem plano alimentar no período. A aderência aparece com o plano publicado.</Text>
                ) : null}
                {dietAdherence && dietAdherence.percent !== undefined ? (
                    <Text>
                        Aderência: {formatNumber(dietAdherence.done ?? 0)} de {dietAdherence.prescribed}{' '}
                        refeições ({formatNumber(dietAdherence.percent)}%)
                    </Text>
                ) : null}
            </View>

            <View style={styles.card} accessibilityLabel="Corpo">
                <Text style={styles.strong}>Corpo</Text>
                {body.error ? <Alert>{body.error}</Alert> : null}
                {!body.data && !body.error ? (
                    <Text accessibilityLiveRegion="polite">Carregando o corpo…</Text>
                ) : null}
                {summaries?.length === 0 ? (
                    <Text>Nenhuma avaliação ainda. Faça a avaliação online para acompanhar.</Text>
                ) : null}
                {summaries?.map((summary) => (
                    <Text key={summary.label}>
                        {summary.label}: {summary.text}
                    </Text>
                ))}
            </View>

            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    box: { padding: 24, gap: 12 },
    title: { fontSize: 20, fontWeight: '600' },
    card: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 12, gap: 6 },
    strong: { fontWeight: '600' },
});
