import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { messageOf, splitErrors, type ProfileApi } from './profileApi';
import {
    CONDITIONS,
    EMPTY_HEALTH,
    fromHealth,
    toHealthRequest,
    validateHealth,
    type Condition,
    type HealthForm,
} from './profileForm';
import { Alert, Button, Choices, Field } from './ui';

const FIELDS = ['conditions', 'notes'] as const;

/**
 * Perfil de saúde do cliente (DEV-0041, FR-08). Dado sensível: vive só no estado desta tela, nunca no
 * armazenamento do aparelho.
 */
export function HealthFormScreen({ api, onDone }: { api: ProfileApi; onDone: () => void }) {
    const [form, setForm] = useState<HealthForm>();
    const [loadError, setLoadError] = useState<string>();
    const [errors, setErrors] = useState<Partial<Record<keyof HealthForm, string>>>({});
    const [formError, setFormError] = useState<string>();
    const [saved, setSaved] = useState(false);
    const [saving, setSaving] = useState(false);
    const [attempt, setAttempt] = useState(0);
    const inFlight = useRef(false);

    useEffect(() => {
        let active = true;
        api.fetchHealth().then(
            (health) => active && setForm(health ? fromHealth(health) : EMPTY_HEALTH),
            (error) => active && setLoadError(messageOf(error)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt]);

    if (loadError) {
        return (
            <View style={styles.form}>
                <Alert>{loadError}</Alert>
                <Button
                    onPress={() => {
                        setLoadError(undefined);
                        setAttempt((current) => current + 1);
                    }}
                >
                    Tentar de novo
                </Button>
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }
    if (!form) return <Text accessibilityLiveRegion="polite">Carregando…</Text>;

    const toggle = (condition: Condition) =>
        setForm(
            (current) =>
                current && {
                    ...current,
                    conditions: current.conditions.includes(condition)
                        ? current.conditions.filter((item) => item !== condition)
                        : [...current.conditions, condition],
                },
        );

    const submit = async () => {
        if (inFlight.current) return;
        setFormError(undefined);
        setSaved(false);
        const found = validateHealth(form);
        setErrors(found);
        if (Object.keys(found).length > 0) return;
        inFlight.current = true;
        setSaving(true);
        try {
            setForm(fromHealth(await api.saveHealth(toHealthRequest(form))));
            setSaved(true);
        } catch (error) {
            const { fieldErrors, formError: general } = splitErrors(error, FIELDS);
            setErrors(fieldErrors);
            setFormError(general);
        } finally {
            inFlight.current = false;
            setSaving(false);
        }
    };

    return (
        <View style={styles.form}>
            <Text accessibilityRole="header" style={styles.title}>
                Perfil de saúde
            </Text>
            <Choices
                label="Condições"
                options={CONDITIONS}
                selected={form.conditions}
                onToggle={toggle}
                error={errors.conditions}
            />
            <Field
                label="Observações"
                multiline
                value={form.notes}
                onChangeText={(notes) => setForm((current) => current && { ...current, notes })}
                error={errors.notes}
            />
            {formError ? <Alert>{formError}</Alert> : null}
            {saved ? <Text accessibilityLiveRegion="polite">Perfil de saúde salvo.</Text> : null}
            <Button onPress={() => void submit()} disabled={saving}>
                {saving ? 'Salvando…' : 'Salvar saúde'}
            </Button>
            <Button onPress={onDone}>Voltar</Button>
        </View>
    );
}

const styles = StyleSheet.create({
    form: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
});
