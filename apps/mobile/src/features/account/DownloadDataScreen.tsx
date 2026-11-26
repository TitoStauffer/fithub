import { useRef, useState } from 'react';
import { Share, StyleSheet, Text, View } from 'react-native';

import { Alert, Button } from '../profile/ui';
import { collectExport, PART_LABELS, type DataExportApi } from './dataExport';

/**
 * Entrega o texto pelo compartilhamento do sistema e diz se a pessoa de fato compartilhou; injetável nos testes. Fechar
 * a folha sem escolher nada não é entrega (no iOS, `dismissedAction`; o Android não distingue e conta como entregue).
 */
export type ShareText = (title: string, message: string) => Promise<boolean>;

const systemShare: ShareText = async (title, message) =>
    (await Share.share({ title, message })).action === Share.sharedAction;

/**
 * Baixar meus dados (DEV-0168, NFR-01, LGPD art. 18, II e V): junta a exportação do client-service, do training e do
 * nutrition num JSON e entrega pelo compartilhamento do sistema. Se uma parte falhar, diz qual e não entrega nada.
 */
export function DownloadDataScreen({
    api,
    onDone,
    share = systemShare,
}: {
    api: DataExportApi;
    onDone: () => void;
    share?: ShareText;
}) {
    const [error, setError] = useState<string>();
    const [working, setWorking] = useState(false);
    const [shared, setShared] = useState(false);
    const inFlight = useRef(false);

    const download = async () => {
        if (inFlight.current) return;
        inFlight.current = true;
        setWorking(true);
        setError(undefined);
        setShared(false);
        try {
            const result = await collectExport(api);
            if (!result.complete) {
                const parts = result.missing.map((part) => PART_LABELS[part]).join(', ');
                setError(`Não foi possível baixar: ${parts}. Nada foi entregue; tente de novo.`);
                return;
            }
            setShared(await share('Meus dados no FitHub', result.json));
        } catch {
            setError('Não foi possível abrir o compartilhamento. Tente de novo.');
        } finally {
            setWorking(false);
            inFlight.current = false;
        }
    };

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Baixar meus dados
            </Text>
            <Text>
                Junta tudo o que o FitHub tem de você (perfil, saúde, avaliações, vínculos, treinos e
                alimentação) num arquivo de texto, para você guardar ou enviar.
            </Text>
            <Text>As fotos vêm por um link que vale 5 minutos: abra ou salve logo depois de baixar.</Text>
            {error ? <Alert>{error}</Alert> : null}
            {shared ? (
                <Text accessibilityLiveRegion="polite">Pronto: seus dados foram entregues.</Text>
            ) : null}
            <Button onPress={() => void download()} disabled={working}>
                {working ? 'Juntando seus dados…' : 'Baixar meus dados'}
            </Button>
            <Button onPress={onDone} disabled={working}>
                Voltar
            </Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 12, alignSelf: 'stretch' },
    title: { fontSize: 20, fontWeight: '600' },
});
