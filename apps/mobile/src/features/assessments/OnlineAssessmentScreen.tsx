import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { OfflineError, type Assessment, type PhotoPose } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Choices, Field } from '../profile/ui';
import type { AssessmentApi, PhotoSource } from './assessmentApi';
import {
    EMPTY_ONLINE_FORM,
    MEASURES,
    estimateWarning,
    measureError,
    navyMeasures,
    toRequest,
    todayIso,
    weightError,
    type OnlineForm,
    type Sex,
} from './onlineSteps';

const POSES: readonly { pose: PhotoPose; label: string; howTo: string }[] = [
    {
        pose: 'FRONT',
        label: 'frente',
        howTo: 'De frente, corpo inteiro, braços um pouco afastados do corpo.',
    },
    { pose: 'SIDE', label: 'lado', howTo: 'De lado, corpo inteiro, braços ao longo do corpo.' },
    { pose: 'BACK', label: 'costas', howTo: 'De costas, corpo inteiro, braços um pouco afastados do corpo.' },
];

const SEXES: { value: Sex; label: string }[] = [
    { value: 'FEMALE', label: 'Feminino' },
    { value: 'MALE', label: 'Masculino' },
];

const OFFLINE_PHOTO =
    'Sem conexão. A foto não ficou guardada no aparelho; tire de novo quando a internet voltar.';

/** Primeiro passo (peso e sexo), um por medida e a revisão. */
const REVIEW_STEP = MEASURES.length + 1;

/**
 * Avaliação online guiada (DEV-0050, FR-17, FR-20): passo a passo com a instrução de cada medida, revisão, envio,
 * o % estimado marcado como online e as fotos por pose. Medidas ficam só na memória da tela, e cada foto é apagada
 * do aparelho logo depois da tentativa de envio.
 */
export function OnlineAssessmentScreen({
    api,
    photos,
    onDone,
    now = new Date(),
}: {
    api: AssessmentApi;
    photos: PhotoSource;
    onDone: () => void;
    now?: Date;
}) {
    const [form, setForm] = useState<OnlineForm>(EMPTY_ONLINE_FORM);
    const [step, setStep] = useState(0);
    const [error, setError] = useState<string>();
    const [formError, setFormError] = useState<string>();
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState<Assessment>();

    if (saved) return <ResultAndPhotos assessment={saved} api={api} photos={photos} onDone={onDone} />;

    const next = () => {
        setError(undefined);
        setStep((current) => current + 1);
    };
    const back = () => {
        setError(undefined);
        setFormError(undefined);
        if (step === 0) onDone();
        else setStep((current) => current - 1);
    };

    const save = async () => {
        if (busy) return;
        setBusy(true);
        setFormError(undefined);
        try {
            setSaved(await api.record(toRequest(form, todayIso(now))));
        } catch (failure) {
            // Sem conexão ou recusa: a tela fica como está, com tudo o que foi preenchido (PRD §14).
            setFormError(messageOf(failure));
        } finally {
            setBusy(false);
        }
    };

    if (step === 0) {
        return (
            <View style={styles.box}>
                <Text accessibilityRole="header" style={styles.title}>
                    Avaliação online
                </Text>
                <Text>Você vai precisar de uma fita métrica e de uma balança. Leva uns 10 minutos.</Text>
                <Field
                    label="Peso (kg)"
                    keyboardType="decimal-pad"
                    value={form.weightKg}
                    error={error}
                    onChangeText={(weightKg) => {
                        setForm((current) => ({ ...current, weightKg }));
                        setError(undefined);
                    }}
                />
                <Choices
                    label="Sexo para as fórmulas"
                    options={SEXES}
                    selected={form.sex ? [form.sex] : []}
                    onToggle={(sex) =>
                        setForm((current) => ({ ...current, sex: current.sex === sex ? undefined : sex }))
                    }
                />
                <Text style={styles.hint}>
                    Usado só no cálculo do % de gordura. Sem ele, a estimativa não sai.
                </Text>
                <Button
                    onPress={() => {
                        const found = weightError(form.weightKg);
                        if (found) setError(found);
                        else next();
                    }}
                >
                    Começar as medidas
                </Button>
                <Button onPress={back}>Cancelar</Button>
            </View>
        );
    }

    if (step < REVIEW_STEP) {
        const measure = MEASURES[step - 1]!;
        const usedInEstimate = navyMeasures(form.sex).includes(measure.key);
        return (
            <View style={styles.box}>
                <Text style={styles.hint}>
                    Medida {step} de {MEASURES.length}
                </Text>
                <Text accessibilityRole="header" style={styles.title}>
                    {measure.label}
                </Text>
                <Text>{measure.howTo}</Text>
                <Field
                    label={`${measure.label} (cm)`}
                    keyboardType="decimal-pad"
                    value={form.measures[measure.key] ?? ''}
                    error={error}
                    onChangeText={(text) => {
                        setForm((current) => ({
                            ...current,
                            measures: { ...current.measures, [measure.key]: text },
                            skipped: current.skipped.filter((key) => key !== measure.key),
                        }));
                        setError(undefined);
                    }}
                />
                <Button
                    onPress={() => {
                        const found = measureError(form.measures[measure.key]);
                        if (found) setError(found);
                        else next();
                    }}
                >
                    Próxima
                </Button>
                <Button
                    onPress={() => {
                        setForm((current) => ({
                            ...current,
                            measures: { ...current.measures, [measure.key]: undefined },
                            skipped: [...current.skipped.filter((key) => key !== measure.key), measure.key],
                        }));
                        next();
                    }}
                >
                    Pular esta medida
                </Button>
                {usedInEstimate ? (
                    <Text style={styles.hint}>Esta medida entra no % de gordura estimado.</Text>
                ) : null}
                <Button onPress={back}>Voltar</Button>
            </View>
        );
    }

    const warning = estimateWarning(form);
    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Revise antes de enviar
            </Text>
            <Text>Peso: {form.weightKg} kg</Text>
            {MEASURES.map(({ key, label }) => (
                <Text key={key}>
                    {label}: {form.skipped.includes(key) ? 'pulada' : `${form.measures[key] ?? ''} cm`}
                </Text>
            ))}
            {warning ? <Text style={styles.warning}>{warning}</Text> : null}
            {formError ? <Alert>{formError}</Alert> : null}
            <Button onPress={() => void save()} disabled={busy}>
                {busy ? 'Enviando…' : 'Enviar avaliação'}
            </Button>
            <Button onPress={back} disabled={busy}>
                Voltar
            </Button>
        </View>
    );
}

function ResultAndPhotos({
    assessment,
    api,
    photos,
    onDone,
}: {
    assessment: Assessment;
    api: AssessmentApi;
    photos: PhotoSource;
    onDone: () => void;
}) {
    const [sent, setSent] = useState<PhotoPose[]>([]);
    const [sending, setSending] = useState<PhotoPose>();
    const [photoError, setPhotoError] = useState<string>();
    const navy = assessment.bodyFat?.find((estimate) => estimate.origin === 'NAVY');

    const send = async (pose: PhotoPose) => {
        if (sending || !assessment.id) return;
        setPhotoError(undefined);
        const shot = await photos.take();
        if (!shot) return;
        setSending(pose);
        try {
            const upload = await api.requestUpload(assessment.id, pose, shot.contentType);
            await api.upload(upload.uploadUrl ?? '', shot.uri, shot.contentType);
            await api.confirm(assessment.id, pose, upload.uploadId ?? '');
            setSent((current) => [...current.filter((item) => item !== pose), pose]);
        } catch (failure) {
            setPhotoError(failure instanceof OfflineError ? OFFLINE_PHOTO : messageOf(failure));
        } finally {
            // A foto não fica no aparelho, tenha o envio dado certo ou não.
            await photos.discard(shot.uri).catch(() => undefined);
            setSending(undefined);
        }
    };

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Avaliação enviada
            </Text>
            {navy ? (
                <Text>% de gordura estimado (online, fórmula da Marinha): {navy.percent}%</Text>
            ) : (
                <Text style={styles.warning}>
                    % de gordura estimado não calculado: faltou alguma medida, o sexo para as fórmulas ou a
                    altura no perfil.
                </Text>
            )}
            <Text accessibilityRole="header" style={styles.subtitle}>
                Fotos de evolução (opcional)
            </Text>
            <Text style={styles.hint}>
                Fundo liso, boa luz, roupa justa. A foto vai direto para o FitHub.
            </Text>
            {POSES.map(({ pose, label, howTo }) => (
                <View key={pose} style={styles.pose}>
                    <Text>{howTo}</Text>
                    {sent.includes(pose) ? <Text>Foto de {label} enviada</Text> : null}
                    <Button onPress={() => void send(pose)} disabled={Boolean(sending)}>
                        {sending === pose
                            ? 'Enviando…'
                            : sent.includes(pose)
                              ? `Tirar outra foto de ${label}`
                              : `Tirar foto de ${label}`}
                    </Button>
                </View>
            ))}
            {photoError ? <Alert>{photoError}</Alert> : null}
            <Button onPress={onDone} disabled={Boolean(sending)}>
                Concluir
            </Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
    subtitle: { fontSize: 16, fontWeight: '600' },
    hint: { color: '#555' },
    warning: { color: '#92400e' },
    pose: { gap: 6 },
});
