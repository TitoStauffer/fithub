import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ApiProblemError, OfflineError, type MealPlanToday } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Field } from '../profile/ui';
import type { MealPhotoSource, MealShot } from './mealPhotoSource';
import { LOG_STATUSES, MAX_DEVIATION, amountText, newLogId, type LogStatus, type MealsApi } from './mealsApi';

/**
 * Registro de uma refeição de hoje (DEV-0090, FR-50, FR-51): como foi (feita, parcial ou não feita), a alternativa
 * usada em cada alimento, o desvio e a foto do prato. O id do registro vem do aparelho e é reaproveitado na correção
 * do mesmo dia; repetir o envio não duplica.
 */
export function MealLogScreen({
    api,
    photos,
    today,
    mealPosition,
    newId = newLogId,
    onDone,
}: {
    api: MealsApi;
    photos: MealPhotoSource;
    today: MealPlanToday;
    mealPosition: number;
    newId?: () => string;
    /** Volta para as refeições de hoje; `saved` diz se o registro foi gravado. */
    onDone: (saved: boolean) => void;
}) {
    const plan = today.plan;
    const meal = plan?.meals?.find((each) => each.position === mealPosition);
    const existing = today.logs?.find((each) => each.mealPosition === mealPosition);
    // O id fica fixo enquanto a tela está aberta: o envio repetido (sem conexão, toque duplo) corrige o mesmo registro.
    const [logId] = useState(() => existing?.id ?? newId());
    const [status, setStatus] = useState<LogStatus | undefined>(existing?.status);
    const [choices, setChoices] = useState<Record<number, number>>(() =>
        Object.fromEntries(
            (existing?.substitutions ?? []).map((each) => [each.itemPosition, each.alternative]),
        ),
    );
    const [deviation, setDeviation] = useState(existing?.deviation ?? '');
    const [shot, setShot] = useState<MealShot>();
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string>();
    const [saving, setSaving] = useState(false);

    if (!plan || !meal) {
        return (
            <View style={styles.box}>
                <Alert>
                    Esta refeição não está mais no plano. Volte e abra as refeições de hoje de novo.
                </Alert>
                <Button onPress={() => onDone(false)}>Voltar</Button>
            </View>
        );
    }

    const choose = (item: number, alternative: number | undefined) =>
        setChoices((current) => {
            const next = { ...current };
            if (alternative === undefined) delete next[item];
            else next[item] = alternative;
            return next;
        });

    const choosePhoto = async (from: 'camera' | 'gallery') => {
        const chosen = from === 'camera' ? await photos.take() : await photos.pick();
        if (!chosen) return;
        if (shot?.fromCamera) await photos.discard(shot.uri).catch(() => undefined);
        setShot(chosen);
    };

    const validate = (): Record<string, string> => {
        const found: Record<string, string> = {};
        if (!status) found.status = 'Escolha como foi a refeição.';
        if (deviation.trim().length > MAX_DEVIATION)
            found.deviation = 'O desvio pode ter até 500 caracteres.';
        return found;
    };

    const save = async () => {
        if (saving) return;
        setError(undefined);
        const found = validate();
        setErrors(found);
        if (Object.keys(found).length > 0 || !status) return;
        setSaving(true);
        try {
            await api.log(logId, {
                planId: plan.id ?? '',
                mealPosition,
                status,
                // A refeição não feita não tem alternativa usada.
                substitutions:
                    status === 'SKIPPED'
                        ? []
                        : Object.entries(choices).map(([item, alternative]) => ({
                              itemPosition: Number(item),
                              alternative,
                          })),
                deviation: deviation.trim() === '' ? undefined : deviation.trim(),
            });
            if (shot) {
                const upload = await api.requestPhotoUpload(logId, shot.contentType);
                await api.uploadPhoto(upload.uploadUrl ?? '', shot.uri, shot.contentType);
                await api.confirmPhoto(logId, upload.uploadId ?? '');
            }
            if (shot?.fromCamera) await photos.discard(shot.uri).catch(() => undefined);
            onDone(true);
        } catch (failure) {
            if (failure instanceof ApiProblemError && failure.status === 409) {
                setError(`Não deu para gravar: ${failure.message}`);
            } else if (failure instanceof ApiProblemError && Object.keys(failure.fieldErrors).length > 0) {
                setErrors(failure.fieldErrors);
            } else if (failure instanceof OfflineError) {
                setError(`${failure.message} O registro fica aqui; envie de novo quando a conexão voltar.`);
            } else {
                setError(messageOf(failure));
            }
        } finally {
            setSaving(false);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                {existing ? 'Corrigir registro' : 'Registrar refeição'}: {meal.name}
            </Text>
            <Text style={styles.label}>Como foi?</Text>
            <View style={styles.row} accessibilityLabel="Como foi a refeição">
                {LOG_STATUSES.map((option) => (
                    <Option
                        key={option.value}
                        label={option.label}
                        checked={status === option.value}
                        onPress={() => setStatus(option.value)}
                    />
                ))}
            </View>
            {errors.status ? <Alert>{errors.status}</Alert> : null}
            {status && status !== 'SKIPPED'
                ? (meal.items ?? []).map((item, index) =>
                      (item.alternatives ?? []).length === 0 ? null : (
                          <View key={`${item.foodId}-${index}`} style={styles.item}>
                              <Text>O que você comeu no lugar de {item.foodName}?</Text>
                              <View style={styles.row}>
                                  <Option
                                      label={`${item.foodName} (${amountText(item)})`}
                                      checked={choices[index] === undefined}
                                      onPress={() => choose(index, undefined)}
                                  />
                                  {(item.alternatives ?? []).map((alternative, position) => (
                                      <Option
                                          key={`${alternative.foodId}-${position}`}
                                          label={`${alternative.foodName} (${alternative.amount} ${alternative.baseUnit === 'ML' ? 'ml' : 'g'})`}
                                          checked={choices[index] === position}
                                          onPress={() => choose(index, position)}
                                      />
                                  ))}
                              </View>
                          </View>
                      ),
                  )
                : null}
            <Field
                label="Desvio (opcional)"
                value={deviation}
                onChangeText={setDeviation}
                multiline
                maxLength={MAX_DEVIATION + 20}
                error={errors.deviation}
                placeholder="Ex.: comi pizza no lugar do jantar"
            />
            <Text>
                {shot
                    ? 'Foto do prato escolhida: vai junto com o registro.'
                    : existing?.photoUrl
                      ? 'Foto do prato já enviada. Escolha outra para trocar.'
                      : 'Foto do prato (opcional)'}
            </Text>
            <View style={styles.row}>
                <Button onPress={() => void choosePhoto('camera')}>Tirar foto</Button>
                <Button onPress={() => void choosePhoto('gallery')}>Escolher da galeria</Button>
            </View>
            {errors.photo ? <Alert>{errors.photo}</Alert> : null}
            {error ? <Alert>{error}</Alert> : null}
            <Button onPress={() => void save()} disabled={saving}>
                {saving ? 'Enviando…' : 'Salvar registro'}
            </Button>
            <Button onPress={() => onDone(false)}>Voltar</Button>
        </ScrollView>
    );
}

function Option({ label, checked, onPress }: { label: string; checked: boolean; onPress: () => void }) {
    return (
        <Pressable
            accessibilityRole="radio"
            accessibilityState={{ checked }}
            accessibilityLabel={label}
            onPress={onPress}
            style={[styles.option, checked ? styles.optionChecked : null]}
        >
            <Text>{label}</Text>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    label: { fontWeight: '600' },
    row: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
    item: { gap: 4 },
    option: { borderWidth: 1, borderRadius: 6, paddingHorizontal: 10, paddingVertical: 6 },
    optionChecked: { backgroundColor: '#dbeafe', borderColor: '#2563eb' },
});
