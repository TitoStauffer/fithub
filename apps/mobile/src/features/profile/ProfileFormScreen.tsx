import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { ClientProfile } from '../../lib/api';
import { splitErrors, type ProfileApi } from './profileApi';
import {
    EMPTY_PROFILE,
    GENDERS,
    GOALS,
    toProfileRequest,
    validateProfile,
    type ProfileErrors,
    type ProfileForm,
} from './profileForm';
import { Alert, Button, Choices, Field } from './ui';

const FIELDS = ['name', 'birthDate', 'gender', 'heightCm', 'goal'] as const;

/**
 * Cadastro e edição do perfil do cliente (DEV-0041, FR-07). A validação espelha a da API; o erro da API aparece
 * no campo sem perder o que foi digitado; um envio por vez; sem conexão, avisa e mantém a tela.
 */
export function ProfileFormScreen({
    api,
    initial = EMPTY_PROFILE,
    today = new Date(),
    onSaved,
    onCancel,
}: {
    api: ProfileApi;
    initial?: ProfileForm;
    today?: Date;
    onSaved: (profile: ClientProfile) => void;
    onCancel?: () => void;
}) {
    const [form, setForm] = useState<ProfileForm>(initial);
    const [errors, setErrors] = useState<ProfileErrors>({});
    const [formError, setFormError] = useState<string>();
    const [saving, setSaving] = useState(false);
    // Trava síncrona: dois toques rápidos não mandam dois pedidos antes de o estado atualizar.
    const inFlight = useRef(false);

    const update = <K extends keyof ProfileForm>(field: K, value: ProfileForm[K]) =>
        setForm((current) => ({ ...current, [field]: value }));

    const submit = async () => {
        if (inFlight.current) return;
        setFormError(undefined);
        const found = validateProfile(form, today);
        setErrors(found);
        if (Object.keys(found).length > 0) return;
        inFlight.current = true;
        setSaving(true);
        try {
            onSaved(await api.saveProfile(toProfileRequest(form)));
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
                {initial === EMPTY_PROFILE ? 'Seu perfil' : 'Editar perfil'}
            </Text>
            <Field
                label="Nome"
                value={form.name}
                onChangeText={(value) => update('name', value)}
                error={errors.name}
            />
            <Field
                label="Data de nascimento"
                placeholder="DD/MM/AAAA"
                keyboardType="numbers-and-punctuation"
                value={form.birthDate}
                onChangeText={(value) => update('birthDate', value)}
                error={errors.birthDate}
            />
            <Choices
                label="Gênero"
                options={GENDERS}
                selected={form.gender === '' ? [] : [form.gender]}
                onToggle={(value) => update('gender', value)}
                error={errors.gender}
            />
            <Field
                label="Altura (cm)"
                keyboardType="number-pad"
                value={form.heightCm}
                onChangeText={(value) => update('heightCm', value)}
                error={errors.heightCm}
            />
            <Choices
                label="Objetivo"
                options={GOALS}
                selected={form.goal === '' ? [] : [form.goal]}
                onToggle={(value) => update('goal', value)}
                error={errors.goal}
            />
            {formError ? <Alert>{formError}</Alert> : null}
            <Button onPress={() => void submit()} disabled={saving}>
                {saving ? 'Salvando…' : 'Salvar perfil'}
            </Button>
            {onCancel ? <Button onPress={onCancel}>Voltar</Button> : null}
        </View>
    );
}

const styles = StyleSheet.create({
    form: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
});
