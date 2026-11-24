import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { ApiProblemError, type ExerciseRecords, type RecordMark } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Field } from '../profile/ui';
import { parseLoad, parseReps } from './sessionForm';
import { formatDate, formatKg, type HistoryApi } from './workoutsApi';

const SOURCE_LABELS: Record<string, string> = {
    SET: 'registrada no treino',
    MANUAL: 'lançado à mão',
};

/** dd/mm/aaaa como aaaa-mm-dd; outro formato, `undefined`. */
export function isoDate(text: string): string | undefined {
    const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text.trim());
    return match ? `${match[3]}-${match[2]}-${match[1]}` : undefined;
}

function todayText(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`;
}

function markText(mark: RecordMark): string {
    return `${formatKg(mark.valueKg ?? 0)} — ${mark.reps} × ${formatKg(mark.loadKg ?? 0)} em ${formatDate(mark.date)}, ${
        SOURCE_LABELS[mark.source ?? ''] ?? ''
    }`;
}

/**
 * Recordes pessoais (DEV-0072, FR-53, FR-54): por exercício, a maior carga e o 1RM estimado, com a data e a origem
 * (série do treino ou lançamento manual). "Lançar recorde" abre o formulário manual; quem valida é a API.
 */
export function RecordsScreen({
    api,
    now = new Date(),
    onDone,
}: {
    api: HistoryApi;
    now?: Date;
    onDone: () => void;
}) {
    const [records, setRecords] = useState<ExerciseRecords[]>();
    const [error, setError] = useState<string>();
    const [notice, setNotice] = useState<string>();
    const [attempt, setAttempt] = useState(0);
    const [adding, setAdding] = useState(false);

    useEffect(() => {
        let active = true;
        api.records().then(
            (found) => active && setRecords(found),
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt]);

    if (adding) {
        return (
            <ManualRecordForm
                api={api}
                today={todayText(now)}
                onSaved={() => {
                    setAdding(false);
                    setNotice('Recorde lançado.');
                    setAttempt((current) => current + 1);
                }}
                onCancel={() => setAdding(false)}
            />
        );
    }

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Recordes
            </Text>
            {notice ? <Text accessibilityLiveRegion="polite">{notice}</Text> : null}
            {error ? (
                <>
                    <Alert>{error}</Alert>
                    <Button
                        onPress={() => {
                            setError(undefined);
                            setAttempt((current) => current + 1);
                        }}
                    >
                        Tentar de novo
                    </Button>
                </>
            ) : null}
            {records === undefined && !error ? (
                <Text accessibilityLiveRegion="polite">Carregando os recordes…</Text>
            ) : null}
            {records?.length === 0 ? (
                <Text>Ainda não há recordes. Eles aparecem com as séries registradas.</Text>
            ) : null}
            {records?.map((record) => (
                <View key={record.exerciseId} style={styles.card} accessibilityLabel={record.exerciseName}>
                    <Text style={styles.strong}>{record.exerciseName}</Text>
                    {record.maxLoad ? <Text>Maior carga: {markText(record.maxLoad)}</Text> : null}
                    {record.estimatedOneRepMax ? (
                        <Text>1RM estimado: {markText(record.estimatedOneRepMax)}</Text>
                    ) : null}
                </View>
            ))}
            <Button onPress={() => setAdding(true)}>Lançar recorde</Button>
            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

/** Recorde feito fora do app: exercício do catálogo, carga, repetições e data (não futura). */
function ManualRecordForm({
    api,
    today,
    onSaved,
    onCancel,
}: {
    api: HistoryApi;
    today: string;
    onSaved: () => void;
    onCancel: () => void;
}) {
    const [query, setQuery] = useState('');
    const [found, setFound] = useState<{ id?: string; name?: string }[]>([]);
    const [exercise, setExercise] = useState<{ id?: string; name?: string }>();
    const [load, setLoad] = useState('');
    const [reps, setReps] = useState('');
    const [date, setDate] = useState(today);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [message, setMessage] = useState<string>();
    const [saving, setSaving] = useState(false);

    const search = async () => {
        setMessage(undefined);
        try {
            setFound(await api.searchExercises(query.trim()));
        } catch (failure) {
            setMessage(messageOf(failure));
        }
    };

    const save = async () => {
        const local: Record<string, string> = {};
        const loadKg = parseLoad(load);
        const repetitions = parseReps(reps);
        const performedOn = isoDate(date);
        if (!exercise?.id) local.exerciseId = 'Escolha o exercício.';
        if (loadKg === undefined) local.loadKg = 'Informe a carga em kg.';
        if (repetitions === undefined) local.reps = 'Informe as repetições.';
        if (!performedOn) local.performedOn = 'Use a data no formato dd/mm/aaaa.';
        setErrors(local);
        setMessage(undefined);
        if (Object.keys(local).length > 0) return;
        setSaving(true);
        try {
            await api.addRecord({
                exerciseId: exercise?.id ?? '',
                loadKg: loadKg ?? 0,
                reps: repetitions ?? 0,
                performedOn: performedOn ?? '',
            });
            onSaved();
        } catch (failure) {
            // A regra é da API: o campo recusado aparece no formulário, e o digitado fica.
            if (failure instanceof ApiProblemError && Object.keys(failure.fieldErrors).length > 0) {
                setErrors(failure.fieldErrors);
            } else {
                setMessage(messageOf(failure));
            }
        } finally {
            setSaving(false);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Lançar recorde
            </Text>
            {message ? <Alert>{message}</Alert> : null}
            {exercise ? (
                <View style={styles.row}>
                    <Text style={styles.strong}>Exercício: {exercise.name}</Text>
                    <Button onPress={() => setExercise(undefined)}>Trocar exercício</Button>
                </View>
            ) : (
                <>
                    <Field
                        label="Buscar exercício"
                        value={query}
                        onChangeText={setQuery}
                        error={errors.exerciseId}
                    />
                    <Button disabled={query.trim().length < 2} onPress={() => void search()}>
                        Buscar
                    </Button>
                    {found.map((option) => (
                        <Button key={option.id} onPress={() => setExercise(option)}>
                            {`Escolher ${option.name ?? ''}`}
                        </Button>
                    ))}
                </>
            )}
            <Field
                label="Carga (kg)"
                value={load}
                onChangeText={setLoad}
                keyboardType="decimal-pad"
                error={errors.loadKg}
            />
            <Field
                label="Repetições"
                value={reps}
                onChangeText={setReps}
                keyboardType="number-pad"
                error={errors.reps}
            />
            <Field label="Data (dd/mm/aaaa)" value={date} onChangeText={setDate} error={errors.performedOn} />
            <Button disabled={saving} onPress={() => void save()}>
                {saving ? 'Salvando…' : 'Salvar recorde'}
            </Button>
            <Button onPress={onCancel}>Cancelar</Button>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch', padding: 4 },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    card: { gap: 4, borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10 },
    strong: { fontWeight: '600' },
    row: { gap: 6 },
});
