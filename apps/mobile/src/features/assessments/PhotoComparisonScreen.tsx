import { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';

import type { Assessment, Photo, PhotoPose } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Choices } from '../profile/ui';
import type { AssessmentApi } from './assessmentApi';

const POSES: { value: PhotoPose; label: string }[] = [
    { value: 'FRONT', label: 'Frente' },
    { value: 'SIDE', label: 'Lado' },
    { value: 'BACK', label: 'Costas' },
];

function dateOf(assessment: Assessment | undefined): string {
    if (!assessment?.measuredOn) return '';
    const [year, month, day] = assessment.measuredOn.split('-');
    return `${day}/${month}/${year}`;
}

/**
 * Comparativo das próprias fotos lado a lado entre duas avaliações (DEV-0051, FR-20). A URL de cada foto é pedida
 * na hora de mostrar, e pedida de novo uma vez se não carregar (URL vencida).
 */
export function PhotoComparisonScreen({ api, onDone }: { api: AssessmentApi; onDone: () => void }) {
    const [assessments, setAssessments] = useState<Assessment[]>();
    const [loadError, setLoadError] = useState<string>();
    const [first, setFirst] = useState<string>();
    const [second, setSecond] = useState<string>();
    const [pose, setPose] = useState<PhotoPose>('FRONT');

    useEffect(() => {
        let active = true;
        api.list().then(
            (found) => active && setAssessments(found),
            (error) => active && setLoadError(messageOf(error)),
        );
        return () => {
            active = false;
        };
    }, [api]);

    if (loadError) {
        return (
            <View style={styles.box}>
                <Alert>{loadError}</Alert>
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }
    if (!assessments) return <Text accessibilityLiveRegion="polite">Carregando suas avaliações…</Text>;
    if (assessments.length < 2) {
        return (
            <View style={styles.box}>
                <Text>Para comparar, você precisa de pelo menos duas avaliações.</Text>
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }

    const options = assessments.map((assessment) => ({
        value: assessment.id ?? '',
        label: dateOf(assessment),
    }));
    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Comparar fotos
            </Text>
            <Choices
                label="Primeira data"
                options={options}
                selected={first ? [first] : []}
                onToggle={(value) => setFirst(value)}
            />
            <Choices
                label="Segunda data"
                options={options}
                selected={second ? [second] : []}
                onToggle={(value) => setSecond(value)}
            />
            <Choices label="Pose" options={POSES} selected={[pose]} onToggle={(value) => setPose(value)} />
            <View style={styles.sides}>
                {[first, second].map((assessmentId, index) =>
                    assessmentId ? (
                        <Side
                            key={`${index}-${assessmentId}-${pose}`}
                            api={api}
                            assessment={assessments.find((item) => item.id === assessmentId)}
                            pose={pose}
                        />
                    ) : (
                        <Text key={index} style={styles.side}>
                            Escolha a {index === 0 ? 'primeira' : 'segunda'} data.
                        </Text>
                    ),
                )}
            </View>
            <Button onPress={onDone}>Voltar</Button>
        </View>
    );
}

function Side({ api, assessment, pose }: { api: AssessmentApi; assessment?: Assessment; pose: PhotoPose }) {
    const [photo, setPhoto] = useState<Photo | null>();
    const [error, setError] = useState<string>();
    const [attempt, setAttempt] = useState(0);
    const [broken, setBroken] = useState(false);
    const assessmentId = assessment?.id ?? '';
    const label = POSES.find((item) => item.value === pose)?.label.toLowerCase() ?? '';

    useEffect(() => {
        let active = true;
        api.photos(assessmentId).then(
            (found) => active && setPhoto(found.find((item) => item.pose === pose) ?? null),
            (failure) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, assessmentId, pose, attempt]);

    return (
        <View style={styles.side}>
            <Text style={styles.date}>{dateOf(assessment)}</Text>
            {error ? <Alert>{error}</Alert> : null}
            {!error && photo === undefined ? <Text>Carregando a foto…</Text> : null}
            {photo === null ? <Text>Sem foto de {label} nesta avaliação.</Text> : null}
            {photo?.url && broken ? <Alert>Não foi possível carregar a foto.</Alert> : null}
            {photo?.url && !broken ? (
                <Image
                    accessibilityLabel={`Foto de ${label} em ${dateOf(assessment)}`}
                    source={{ uri: photo.url }}
                    style={styles.photo}
                    resizeMode="contain"
                    onError={() => {
                        // URL vencida: pede uma nova uma vez; se a nova também falhar, avisa.
                        if (attempt > 0) setBroken(true);
                        else setAttempt(1);
                    }}
                />
            ) : null}
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
    sides: { flexDirection: 'row', gap: 8 },
    side: { flex: 1, gap: 4 },
    date: { fontWeight: '600' },
    photo: { width: '100%', aspectRatio: 3 / 4 },
});
