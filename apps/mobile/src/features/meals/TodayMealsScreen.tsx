import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import type { Meal, MealItem, MealLog, MealPlanToday } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button } from '../profile/ui';
import { LOG_STATUS_LABELS, amountText, differenceText, macrosText, type MealsApi } from './mealsApi';

/**
 * Refeições de hoje (DEV-0089, FR-50): o plano alimentar publicado, com cada refeição na ordem (horário e observação),
 * os alimentos com a quantidade (ou a medida caseira), as alternativas com a diferença de macros, os macros da
 * refeição e do dia e o registro de hoje de cada refeição.
 */
export function TodayMealsScreen({
    api,
    onLog,
    onDone,
}: {
    api: MealsApi;
    /** Leva ao registro da refeição (DEV-0090); sem ele, a tela só mostra. */
    onLog?: (today: MealPlanToday, mealPosition: number) => void;
    onDone: () => void;
}) {
    const [today, setToday] = useState<MealPlanToday | null>();
    const [error, setError] = useState<string>();
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        // Falha de conexão avisa, mas mantém na tela o que já tinha carregado (PRD §14).
        api.today().then(
            (found) => active && setToday(found),
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

    if (today === undefined) {
        return (
            <View style={styles.box}>
                {error ? (
                    <>
                        <Alert>{error}</Alert>
                        <Button onPress={load}>Tentar de novo</Button>
                    </>
                ) : (
                    <Text accessibilityLiveRegion="polite">Carregando suas refeições…</Text>
                )}
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }

    const plan = today?.plan;
    const logs = today?.logs ?? [];
    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Refeições de hoje
            </Text>
            {error ? (
                <>
                    <Alert>{error}</Alert>
                    <Button onPress={load}>Tentar de novo</Button>
                </>
            ) : null}
            {!plan ? (
                <Text>
                    Você ainda não tem plano alimentar. Quando a sua nutricionista publicar, ele aparece aqui.
                </Text>
            ) : (
                <>
                    <Text style={styles.subtitle}>{plan.name}</Text>
                    {plan.note ? <Text>{plan.note}</Text> : null}
                    {(plan.meals ?? []).map((meal) => (
                        <MealCard
                            key={meal.position}
                            meal={meal}
                            log={logs.find((each) => each.mealPosition === meal.position)}
                            onLog={onLog && today ? () => onLog(today, meal.position ?? 0) : undefined}
                        />
                    ))}
                    <Text style={styles.total}>Total do dia: {macrosText(plan.day)}</Text>
                </>
            )}
            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

function MealCard({ meal, log, onLog }: { meal: Meal; log: MealLog | undefined; onLog?: () => void }) {
    return (
        <View accessibilityLabel={`Refeição ${meal.name}`} style={styles.card}>
            <Text style={styles.subtitle}>
                {meal.suggestedTime ? `${meal.suggestedTime} · ` : ''}
                {meal.name}
            </Text>
            {meal.note ? <Text>{meal.note}</Text> : null}
            {(meal.items ?? []).map((item, index) => (
                <ItemLine key={`${item.foodId}-${index}`} item={item} />
            ))}
            <Text>Total da refeição: {macrosText(meal.total)}</Text>
            <Text accessibilityLiveRegion="polite">
                {log
                    ? `Registrada hoje: ${LOG_STATUS_LABELS[log.status ?? ''] ?? log.status}`
                    : 'Ainda não registrada'}
            </Text>
            {onLog ? (
                <Button onPress={onLog}>{log ? 'Corrigir registro' : 'Registrar refeição'}</Button>
            ) : null}
        </View>
    );
}

function ItemLine({ item }: { item: MealItem }) {
    return (
        <View style={styles.item}>
            <Text>
                {item.foodName} — {amountText(item)}
            </Text>
            <Text style={styles.muted}>{macrosText(item.nutrients)}</Text>
            {(item.alternatives ?? []).map((alternative, index) => (
                <Text key={`${alternative.foodId}-${index}`} style={styles.muted}>
                    {index === 0 ? 'Ou' : 'Ou ainda'}: {alternative.foodName} — {alternative.amount}{' '}
                    {alternative.baseUnit === 'ML' ? 'ml' : 'g'} ({differenceText(alternative.difference)})
                </Text>
            ))}
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    subtitle: { fontWeight: '600' },
    card: { gap: 6, borderWidth: 1, borderRadius: 8, padding: 10 },
    item: { gap: 2 },
    muted: { color: '#555' },
    total: { fontWeight: '600' },
});
