import { useEffect, useState } from 'react';
import { Linking, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { ActivePlan, ClientExercise, ClientWorkoutItem } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button } from '../profile/ui';
import {
    formatDate,
    formatKg,
    newSessionId,
    setsAndReps,
    TECHNIQUE_LABELS,
    type WorkoutsApi,
} from './workoutsApi';

/**
 * Treino de hoje (DEV-0070, FR-47): cada ficha ativa (a do personal e, se houver, a própria) com o próximo treino da
 * sequência e a prescrição de cada exercício. A mídia abre fora do app, pelo link. "Começar treino" inicia a sessão
 * com o id gerado no aparelho; com sessão aberta, a ficha mostra "Continuar treino".
 */
export function TodayWorkoutScreen({
    api,
    onOpenSession,
    openUrl = (url) => Linking.openURL(url),
    newId = newSessionId,
    onDone,
}: {
    api: WorkoutsApi;
    /** Leva ao registro das séries (DEV-0071); sem ele, a tela só avisa que o treino começou. */
    onOpenSession?: (sessionId: string, planId: string) => void;
    openUrl?: (url: string) => Promise<unknown>;
    newId?: () => string;
    onDone: () => void;
}) {
    const [plans, setPlans] = useState<ActivePlan[]>();
    const [error, setError] = useState<string>();
    const [notice, setNotice] = useState<string>();
    const [starting, setStarting] = useState<string>();

    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        // Falha de conexão avisa, mas mantém na tela o que já tinha carregado (PRD §14).
        api.activePlans().then(
            (found) => active && setPlans(found),
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt]);

    const load = () => {
        setError(undefined);
        setAttempt((current) => current + 1);
    };

    const open = (sessionId: string, planId: string) => {
        if (onOpenSession) onOpenSession(sessionId, planId);
        else setNotice('Treino iniciado. O registro das séries chega na próxima versão do app.');
    };

    const start = async (plan: ActivePlan) => {
        const planId = plan.planId ?? '';
        setStarting(planId);
        setNotice(undefined);
        try {
            const session = await api.start(planId, newId());
            setPlans((current) =>
                current?.map((each) =>
                    each.planId === planId ? { ...each, openSessionId: session.id } : each,
                ),
            );
            open(session.id ?? '', planId);
        } catch (failure) {
            setError(messageOf(failure));
        } finally {
            setStarting(undefined);
        }
    };

    if (plans === undefined) {
        return (
            <View style={styles.box}>
                {error ? (
                    <>
                        <Alert>{error}</Alert>
                        <Button onPress={load}>Tentar de novo</Button>
                    </>
                ) : (
                    <Text accessibilityLiveRegion="polite">Carregando seu treino…</Text>
                )}
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Treino de hoje
            </Text>
            {error ? (
                <>
                    <Alert>{error}</Alert>
                    <Button onPress={load}>Tentar de novo</Button>
                </>
            ) : null}
            {notice ? <Text accessibilityLiveRegion="polite">{notice}</Text> : null}
            {plans.length === 0 ? (
                <Text>
                    Você ainda não tem ficha de treino. Peça a ficha ao seu personal, ou monte a sua própria
                    em breve aqui no app.
                </Text>
            ) : (
                plans.map((plan) => (
                    <PlanCard
                        key={plan.planId}
                        plan={plan}
                        starting={starting === plan.planId}
                        onStart={() => void start(plan)}
                        onContinue={() => open(plan.openSessionId ?? '', plan.planId ?? '')}
                        openUrl={openUrl}
                    />
                ))
            )}
            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

function PlanCard({
    plan,
    starting,
    onStart,
    onContinue,
    openUrl,
}: {
    plan: ActivePlan;
    starting: boolean;
    onStart: () => void;
    onContinue: () => void;
    openUrl: (url: string) => Promise<unknown>;
}) {
    const division = plan.divisions?.find((each) => each.position === plan.nextDivision);
    const title = `Próximo: Treino ${plan.nextDivisionLetter ?? ''}`;
    return (
        <View style={styles.card} accessibilityLabel={`Ficha ${plan.name ?? ''}`}>
            <Text style={styles.planName}>{plan.name}</Text>
            {plan.expired ? (
                <Text accessibilityRole="alert" style={styles.warning}>
                    Esta ficha venceu em {formatDate(plan.expiresOn)}. Você continua treinando nela; peça a
                    nova ao seu personal.
                </Text>
            ) : null}
            <Text accessibilityRole="header" style={styles.next}>
                {division?.name ? `${title} — ${division.name}` : title}
            </Text>
            {(division?.items ?? []).map((item, index) => (
                <ExerciseRow key={`${item.exercise?.id ?? ''}-${index}`} item={item} openUrl={openUrl} />
            ))}
            {plan.openSessionId ? (
                <Button onPress={onContinue}>Continuar treino</Button>
            ) : (
                <Button onPress={onStart} disabled={starting}>
                    {starting ? 'Começando…' : 'Começar treino'}
                </Button>
            )}
        </View>
    );
}

function ExerciseRow({
    item,
    openUrl,
}: {
    item: ClientWorkoutItem;
    openUrl: (url: string) => Promise<unknown>;
}) {
    const name = item.exercise?.name ?? 'Exercício';
    const details = [
        setsAndReps(item),
        item.loadKg != null ? `carga sugerida ${formatKg(item.loadKg)}` : null,
        item.restSeconds != null ? `descanso ${item.restSeconds} s` : null,
        item.technique ? TECHNIQUE_LABELS[item.technique] : null,
    ].filter(Boolean);
    const alternatives = (item.alternatives ?? []).map((alternative) => alternative.name).filter(Boolean);
    return (
        <View style={styles.exercise} accessibilityLabel={name}>
            <Text style={styles.exerciseName}>{name}</Text>
            <Text>{details.join(' · ')}</Text>
            {item.note ? <Text>Observação: {item.note}</Text> : null}
            {alternatives.length > 0 ? <Text>Alternativas: {alternatives.join(', ')}</Text> : null}
            {item.exercise?.mediaUrl ? (
                <Button onPress={() => void openUrl(item.exercise?.mediaUrl ?? '')}>
                    Ver vídeo de {name}
                </Button>
            ) : null}
            <Credit exercise={item.exercise} />
        </View>
    );
}

/** Crédito do exercício importado (ADR-010): fonte, licença e autor, e o da mídia quando houver. */
function Credit({ exercise }: { exercise?: ClientExercise }) {
    const attribution = exercise?.attribution;
    if (!attribution) return null;
    const media =
        attribution.mediaAuthor && attribution.mediaLicense
            ? ` Mídia: ${attribution.mediaAuthor} (${attribution.mediaLicense}).`
            : '';
    return (
        <Text style={styles.credit}>
            Fonte: {attribution.source} ({attribution.license}), por {attribution.author}.{media}
        </Text>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch', padding: 4 },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    card: { gap: 8, borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10 },
    planName: { fontSize: 16, fontWeight: '600' },
    next: { fontWeight: '600' },
    warning: { color: '#92400e' },
    exercise: { gap: 2, paddingVertical: 4 },
    exerciseName: { fontWeight: '600' },
    credit: { fontSize: 12, color: '#555' },
});
