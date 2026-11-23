import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { ClientLink } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button } from '../profile/ui';
import { PROFESSIONAL_LABELS, type LinksApi } from './linksApi';

/**
 * Vínculos do cliente (DEV-0042, FR-12): ativos e encerrados. Encerrar pede confirmação na própria tela e corta o
 * acesso do profissional na hora (BR-05).
 */
export function LinksScreen({ api, onDone }: { api: LinksApi; onDone: () => void }) {
    const [links, setLinks] = useState<ClientLink[]>();
    const [error, setError] = useState<string>();
    const [confirming, setConfirming] = useState<string>();
    const [ending, setEnding] = useState(false);
    const inFlight = useRef(false);

    const [version, setVersion] = useState(0);

    useEffect(() => {
        let active = true;
        api.list().then(
            (found) => active && setLinks(found),
            (failure: unknown) => active && setError(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, version]);

    const load = () => {
        setError(undefined);
        setVersion((current) => current + 1);
    };

    const end = async (linkId: string) => {
        if (inFlight.current) return;
        inFlight.current = true;
        setEnding(true);
        try {
            await api.end(linkId);
            setConfirming(undefined);
            load();
        } catch (failure) {
            setError(messageOf(failure));
        } finally {
            inFlight.current = false;
            setEnding(false);
        }
    };

    if (!links) {
        return (
            <View style={styles.box}>
                {error ? <Alert>{error}</Alert> : <Text accessibilityLiveRegion="polite">Carregando…</Text>}
                {error ? <Button onPress={load}>Tentar de novo</Button> : null}
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Meus vínculos
            </Text>
            {links.length === 0 ? <Text>Você ainda não tem vínculos.</Text> : null}
            {links.map((link) => (
                <View key={link.id} style={styles.item}>
                    <Text style={styles.name}>{link.professionalName}</Text>
                    <Text>
                        {PROFESSIONAL_LABELS[link.professionalType ?? ''] ?? ''} ·{' '}
                        {link.status === 'ACTIVE' ? 'Ativo' : 'Encerrado'}
                    </Text>
                    {link.status === 'ACTIVE' && confirming !== link.id ? (
                        <Button onPress={() => setConfirming(link.id)}>
                            Encerrar vínculo com {link.professionalName}
                        </Button>
                    ) : null}
                    {confirming === link.id ? (
                        <View style={styles.confirm}>
                            <Text>
                                {link.professionalName} vai perder o acesso aos seus dados na hora. Encerrar
                                mesmo?
                            </Text>
                            <Button disabled={ending} onPress={() => void end(link.id ?? '')}>
                                Confirmar encerramento
                            </Button>
                            <Button onPress={() => setConfirming(undefined)}>Cancelar</Button>
                        </View>
                    ) : null}
                </View>
            ))}
            {error ? <Alert>{error}</Alert> : null}
            <Button onPress={onDone}>Voltar</Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignSelf: 'stretch' },
    title: { fontSize: 18, fontWeight: '600' },
    item: { gap: 4, borderWidth: 1, borderColor: '#eee', borderRadius: 6, padding: 8 },
    name: { fontWeight: '600' },
    confirm: { gap: 6 },
});
