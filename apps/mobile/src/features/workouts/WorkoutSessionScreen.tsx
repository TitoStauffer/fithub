import { useEffect, useRef, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import type { RecordedSet, SessionItem, SessionView } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Choices, Field } from '../profile/ui';
import { FEELING_LABELS, parseLoad, parseReps, prefill, type SetDraft } from './sessionForm';
import { newSessionId, setsAndReps, type SessionApi } from './workoutsApi';

/**
 * Registro do treino por série (DEV-0071, FR-48, FR-49). Cada série vem preenchida (último treino ou prescrito) e
 * "Confirmar" grava com um toque; os campos permitem ajustar antes. O id da série é gerado no aparelho, então o
 * toque duplo e o reenvio não duplicam. Trocar pela alternativa muda o exercício das próximas séries do item. Sem
 * conexão, a tela avisa e mantém o que foi digitado (PRD §14).
 */
export function WorkoutSessionScreen({
    api,
    sessionId,
    newId = newSessionId,
    onFinished,
    onBack,
}: {
    api: SessionApi;
    sessionId: string;
    newId?: () => string;
    onFinished: () => void;
    onBack: () => void;
}) {
    const [session, setSession] = useState<SessionView>();
    const [error, setError] = useState<string>();
    const [attempt, setAttempt] = useState(0);
    // Exercício escolhido por item (o do item ou uma alternativa), digitado por série e id de cada série nova.
    const [chosen, setChosen] = useState<Record<number, string>>({});
    const [drafts, setDrafts] = useState<Record<string, SetDraft>>({});
    // O id da série nova nasce no primeiro toque e vale para os reenvios: ref, para o toque duplo já ver o mesmo id.
    const newIds = useRef<Record<string, string>>({});
    const [busy, setBusy] = useState<string>();
    const [comment, setComment] = useState('');
    const [feeling, setFeeling] = useState<string>();

    useEffect(() => {
        let active = true;
        api.view(sessionId).then(
            (found) => {
                if (!active) return;
                setSession(found);
                setComment(found.comment ?? '');
                setFeeling(found.feeling == null ? undefined : String(found.feeling));
            },
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, sessionId, attempt]);

    if (!session) {
        return (
            <View style={styles.box}>
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
                ) : (
                    <Text accessibilityLiveRegion="polite">Carregando o treino…</Text>
                )}
                <Button onPress={onBack}>Voltar</Button>
            </View>
        );
    }

    const correctable = session.correctable !== false;

    const replaceSet = (position: number, saved: RecordedSet | undefined, removedId?: string) => {
        setSession((current) =>
            current
                ? {
                      ...current,
                      items: current.items?.map((item) => {
                          if (item.position !== position) return item;
                          const others = (item.recorded ?? []).filter(
                              (each) => each.id !== (saved?.id ?? removedId),
                          );
                          const recorded = saved ? [...others, saved] : others;
                          return {
                              ...item,
                              recorded: recorded.sort((a, b) => (a.setNumber ?? 0) - (b.setNumber ?? 0)),
                          };
                      }),
                  }
                : current,
        );
    };

    /**
     * Grava a série. {@code doneWith} é o exercício feito: na série nova, o escolhido agora; na correção, o que a
     * série gravou, para corrigir carga e repetições não trocar o exercício sem o aluno pedir.
     */
    const confirm = async (
        item: SessionItem,
        setNumber: number,
        draft: SetDraft,
        setId: string,
        key: string,
        doneWith: string | undefined,
    ) => {
        const loadKg = parseLoad(draft.load);
        const reps = parseReps(draft.reps);
        if (loadKg === undefined || reps === undefined) {
            setError('Informe a carga (em kg) e as repetições da série.');
            return;
        }
        const position = item.position ?? 0;
        setBusy(key);
        setError(undefined);
        try {
            const saved = await api.record(sessionId, setId, {
                itemPosition: position,
                setNumber,
                loadKg,
                reps,
                exerciseId: doneWith && doneWith !== item.exercise?.id ? doneWith : null,
            });
            replaceSet(position, saved);
            // Gravada, a série mostra o que está no servidor: o rascunho sai.
            setDrafts((all) => {
                const rest = { ...all };
                delete rest[key];
                return rest;
            });
        } catch (failure) {
            // Sem conexão: o valor digitado fica, e o reenvio usa o mesmo id.
            setError(messageOf(failure));
        } finally {
            setBusy(undefined);
        }
    };

    const remove = async (item: SessionItem, set: RecordedSet, key: string) => {
        setBusy(key);
        setError(undefined);
        try {
            await api.remove(sessionId, set.id ?? '');
            replaceSet(item.position ?? 0, undefined, set.id);
        } catch (failure) {
            setError(messageOf(failure));
        } finally {
            setBusy(undefined);
        }
    };

    const finish = async () => {
        setBusy('finish');
        setError(undefined);
        try {
            const newComment = comment.trim() === '' ? null : comment.trim();
            const newFeeling = feeling ? Number(feeling) : null;
            // Envia sempre que mudou do que veio, inclusive quando o aluno apaga o comentário ou a sensação.
            if (newComment !== (session.comment ?? null) || newFeeling !== (session.feeling ?? null)) {
                await api.feedback(sessionId, {
                    comment: newComment,
                    feeling: newFeeling,
                });
            }
            if (!session.finishedAt) await api.finish(sessionId);
            onFinished();
        } catch (failure) {
            setError(messageOf(failure));
        } finally {
            setBusy(undefined);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                {session.divisionName
                    ? `Treino ${session.divisionLetter ?? ''} — ${session.divisionName}`
                    : `Treino ${session.divisionLetter ?? ''}`}
            </Text>
            {correctable ? null : (
                <Text accessibilityRole="alert" style={styles.warning}>
                    Este treino é de outro dia: já não dá para registrar nem corrigir as séries.
                </Text>
            )}
            {error ? <Alert>{error}</Alert> : null}
            {(session.items ?? []).map((item) => {
                const position = item.position ?? 0;
                const exerciseId = chosen[position] ?? item.exercise?.id;
                const options = [item.exercise, ...(item.alternatives ?? [])].filter(
                    (each) => each && each.id !== exerciseId,
                );
                const current = [item.exercise, ...(item.alternatives ?? [])].find(
                    (each) => each?.id === exerciseId,
                );
                return (
                    <View
                        key={position}
                        style={styles.card}
                        accessibilityLabel={current?.name ?? 'Exercício'}
                    >
                        <Text style={styles.exerciseName}>{current?.name}</Text>
                        <Text>Prescrito: {setsAndReps(item)}</Text>
                        {correctable
                            ? options.map((option) => (
                                  <Button
                                      key={option?.id}
                                      onPress={() =>
                                          setChosen((all) => ({ ...all, [position]: option?.id ?? '' }))
                                      }
                                  >
                                      {option?.id === item.exercise?.id
                                          ? `Voltar para ${option?.name ?? ''}`
                                          : `Trocar por ${option?.name ?? ''}`}
                                  </Button>
                              ))
                            : null}
                        {Array.from({ length: item.sets ?? 0 }, (_, index) => index + 1).map((setNumber) => {
                            const key = `${position}-${setNumber}`;
                            const recorded = item.recorded?.find((each) => each.setNumber === setNumber);
                            const draft =
                                drafts[key] ??
                                (recorded
                                    ? {
                                          load: String(recorded.loadKg ?? 0).replace('.', ','),
                                          reps: String(recorded.reps ?? 0),
                                      }
                                    : prefill(item, setNumber, exerciseId));
                            // A série registrada mostra e corrige o exercício que gravou, não o escolhido agora.
                            const setExerciseId = recorded
                                ? (recorded.exerciseId ?? item.exercise?.id)
                                : exerciseId;
                            const setName = [item.exercise, ...(item.alternatives ?? [])].find(
                                (each) => each?.id === setExerciseId,
                            )?.name;
                            const label = `${setName ?? current?.name ?? ''}, série ${setNumber}`;
                            const edit = (change: Partial<SetDraft>) =>
                                setDrafts((all) => ({ ...all, [key]: { ...draft, ...change } }));
                            return (
                                <View key={key} style={styles.set}>
                                    <Text>
                                        Série {setNumber}
                                        {recorded ? ' — registrada' : ''}
                                    </Text>
                                    <View style={styles.row}>
                                        <Field
                                            label={`Carga (kg) de ${label}`}
                                            value={draft.load}
                                            onChangeText={(load) => edit({ load })}
                                            keyboardType="decimal-pad"
                                            editable={correctable}
                                        />
                                        <Field
                                            label={`Repetições de ${label}`}
                                            value={draft.reps}
                                            onChangeText={(reps) => edit({ reps })}
                                            keyboardType="number-pad"
                                            editable={correctable}
                                        />
                                    </View>
                                    {correctable ? (
                                        <View style={styles.row}>
                                            <Button
                                                disabled={busy === key}
                                                onPress={() => {
                                                    newIds.current[key] ??= newId();
                                                    const id = recorded?.id ?? newIds.current[key];
                                                    void confirm(
                                                        item,
                                                        setNumber,
                                                        draft,
                                                        id,
                                                        key,
                                                        setExerciseId,
                                                    );
                                                }}
                                            >
                                                {recorded ? `Corrigir ${label}` : `Confirmar ${label}`}
                                            </Button>
                                            {recorded ? (
                                                <Button
                                                    disabled={busy === key}
                                                    onPress={() => void remove(item, recorded, key)}
                                                >
                                                    {`Apagar ${label}`}
                                                </Button>
                                            ) : null}
                                        </View>
                                    ) : null}
                                </View>
                            );
                        })}
                    </View>
                );
            })}
            {correctable ? (
                <>
                    <Field
                        label="Comentário do treino"
                        value={comment}
                        onChangeText={setComment}
                        multiline
                        maxLength={500}
                    />
                    <Choices
                        label="Como foi o treino?"
                        options={Object.entries(FEELING_LABELS).map(([value, text]) => ({
                            value,
                            label: text,
                        }))}
                        selected={feeling ? [feeling] : []}
                        onToggle={(value) =>
                            setFeeling((currentValue) => (currentValue === value ? undefined : value))
                        }
                    />
                    <Button disabled={busy === 'finish'} onPress={() => void finish()}>
                        {busy === 'finish'
                            ? 'Finalizando…'
                            : session.finishedAt
                              ? 'Salvar e voltar'
                              : 'Finalizar treino'}
                    </Button>
                </>
            ) : null}
            <Button onPress={onBack}>Voltar</Button>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch', padding: 4 },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    warning: { color: '#92400e' },
    card: { gap: 6, borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10 },
    exerciseName: { fontWeight: '600' },
    set: { gap: 4 },
    row: { flexDirection: 'row', gap: 6 },
});
