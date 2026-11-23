import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { ApiProblemError, type InvitationPreview } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Choices, Field } from '../profile/ui';
import { PROFESSIONAL_LABELS, SHARED_DATA_LABELS, type LinksApi } from './linksApi';

type Target = { token: string } | { code: string };

/** Mensagem certa para cada recusa da API na consulta do convite. */
function lookupMessage(error: unknown): string {
    if (error instanceof ApiProblemError && error.status === 404) {
        return 'Convite não encontrado. Confira o código e tente de novo.';
    }
    return messageOf(error);
}

/**
 * Aceite do convite (DEV-0042, FR-11): pelo link (token) ou digitando o código do convite ou o código pessoal do
 * profissional. Mostra exatamente o que será compartilhado e a versão do termo, e só aceita com a confirmação
 * explícita. Recusar volta sem chamar a API: nada é criado.
 */
export function AcceptInvitationScreen({
    api,
    token,
    onDone,
}: {
    api: LinksApi;
    token?: string;
    onDone: (accepted: boolean) => void;
}) {
    const [code, setCode] = useState('');
    const [target, setTarget] = useState<Target | undefined>(token ? { token } : undefined);
    const [preview, setPreview] = useState<InvitationPreview>();
    const [agreed, setAgreed] = useState(false);
    const [error, setError] = useState<string>();
    const [busy, setBusy] = useState(false);
    const inFlight = useRef(false);

    useEffect(() => {
        if (!target) return;
        let active = true;
        api.lookup(target).then(
            (found) => active && setPreview(found),
            (failure: unknown) => {
                if (!active) return;
                setError(lookupMessage(failure));
                setTarget(undefined);
            },
        );
        return () => {
            active = false;
        };
    }, [api, target]);

    const accept = async () => {
        if (!preview || !target || inFlight.current) return;
        inFlight.current = true;
        setBusy(true);
        setError(undefined);
        try {
            await api.accept({ ...target, consent: true, termsVersion: preview.termsVersion });
            onDone(true);
        } catch (failure) {
            // 409 (vínculo do mesmo tipo, termo mudou) e 410 (convite sem validade) trazem a orientação da API.
            setError(messageOf(failure));
        } finally {
            inFlight.current = false;
            setBusy(false);
        }
    };

    if (!preview) {
        // Consultando: há um alvo e ainda não veio a resposta.
        const lookingUp = target !== undefined;
        return (
            <View style={styles.box}>
                <Text accessibilityRole="header" style={styles.title}>
                    Aceitar convite
                </Text>
                {lookingUp ? <Text accessibilityLiveRegion="polite">Consultando o convite…</Text> : null}
                <Field
                    label="Código do convite ou do profissional"
                    autoCapitalize="characters"
                    value={code}
                    onChangeText={setCode}
                />
                {error ? <Alert>{error}</Alert> : null}
                <Button
                    disabled={lookingUp || code.trim().length === 0}
                    onPress={() => {
                        setError(undefined);
                        setTarget({ code: code.trim() });
                    }}
                >
                    Continuar
                </Button>
                <Button onPress={() => onDone(false)}>Voltar</Button>
            </View>
        );
    }

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                {preview.professionalName} quer acompanhar você
            </Text>
            <Text>{PROFESSIONAL_LABELS[preview.professionalType ?? ''] ?? ''}</Text>
            <Text style={styles.label}>Ao aceitar, este profissional vai ver:</Text>
            {(preview.sharedData ?? []).map((item) => (
                <Text key={item}>• {SHARED_DATA_LABELS[item] ?? item}</Text>
            ))}
            <Text>{preview.termsText}</Text>
            <Text style={styles.version}>Termo de consentimento, versão {preview.termsVersion}</Text>
            <Choices
                label="Consentimento"
                options={[{ value: 'agree', label: 'Li e concordo em compartilhar esses dados' }]}
                selected={agreed ? ['agree'] : []}
                onToggle={() => setAgreed((current) => !current)}
            />
            {error ? <Alert>{error}</Alert> : null}
            <Button disabled={!agreed || busy} onPress={() => void accept()}>
                {busy ? 'Aceitando…' : 'Aceitar'}
            </Button>
            <Button onPress={() => onDone(false)}>Recusar</Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
    label: { fontWeight: '600' },
    version: { color: '#555' },
});
