import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { HealthConsentTerms } from '../../lib/api';
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

const FIELDS = ['conditions', 'notes', 'consent'] as const;

/**
 * Perfil de saúde do cliente (DEV-0041, FR-08). Dado sensível: vive só no estado desta tela, nunca no
 * armazenamento do aparelho. Enquanto a conta não aceitou o termo vigente do perfil de saúde (DEV-0179), a tela mostra
 * o texto e uma caixa de aceite desmarcada, e só envia com ela marcada.
 */
export function HealthFormScreen({ api, onDone }: { api: ProfileApi; onDone: () => void }) {
    const [form, setForm] = useState<HealthForm>();
    const [terms, setTerms] = useState<HealthConsentTerms>();
    const [agreed, setAgreed] = useState(false);
    const [loadError, setLoadError] = useState<string>();
    const [errors, setErrors] = useState<Partial<Record<(typeof FIELDS)[number], string>>>({});
    const [formError, setFormError] = useState<string>();
    const [saved, setSaved] = useState(false);
    const [saving, setSaving] = useState(false);
    const [attempt, setAttempt] = useState(0);
    const inFlight = useRef(false);

    useEffect(() => {
        let active = true;
        Promise.all([api.fetchHealth(), api.fetchHealthTerms()]).then(
            ([health, current]) => {
                if (!active) return;
                setForm(health ? fromHealth(health) : EMPTY_HEALTH);
                setTerms(current);
            },
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
    if (!form || !terms) return <Text accessibilityLiveRegion="polite">Carregando…</Text>;

    const needsConsent = !terms.accepted;

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
        const found: Partial<Record<(typeof FIELDS)[number], string>> = validateHealth(form);
        if (needsConsent && !agreed) found.consent = 'Para salvar, aceite o termo do perfil de saúde.';
        setErrors(found);
        if (Object.keys(found).length > 0) return;
        inFlight.current = true;
        setSaving(true);
        try {
            const body = needsConsent
                ? { ...toHealthRequest(form), consent: true, termsVersion: terms.termsVersion }
                : toHealthRequest(form);
            setForm(fromHealth(await api.saveHealth(body)));
            if (needsConsent) setTerms({ ...terms, accepted: true });
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
            {needsConsent ? (
                <View style={styles.terms}>
                    <Text style={styles.termsTitle}>
                        Termo do perfil de saúde, versão {terms.termsVersion}
                    </Text>
                    <Text>{terms.termsText}</Text>
                    <Pressable
                        accessibilityRole="checkbox"
                        accessibilityState={{ checked: agreed }}
                        accessibilityLabel="Li e aceito o termo do perfil de saúde"
                        onPress={() => setAgreed((current) => !current)}
                        style={[styles.consent, agreed ? styles.consentChecked : null]}
                    >
                        <Text>Li e aceito o termo do perfil de saúde</Text>
                    </Pressable>
                    {errors.consent ? <Text style={styles.error}>{errors.consent}</Text> : null}
                </View>
            ) : null}
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
    terms: { gap: 6 },
    termsTitle: { fontWeight: '600' },
    consent: { borderWidth: 1, borderColor: '#999', borderRadius: 6, padding: 10 },
    consentChecked: { borderColor: '#1a7f37', backgroundColor: '#e6f4ea' },
    error: { color: '#b3261e' },
});
