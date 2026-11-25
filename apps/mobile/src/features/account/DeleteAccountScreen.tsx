import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { messageOf } from '../profile/profileApi';
import { Alert, Button, Field } from '../profile/ui';
import { CONFIRMATION_WORD, isConfirmed, type AccountApi } from './accountApi';

/**
 * Exclusão da conta pelo cliente (DEV-0164, NFR-01, ADR-012). Diz o que é apagado e o que fica, pede que o cliente
 * digite a confirmação e só então chama a API. Com sucesso, encerra a sessão; com erro, nada muda.
 */
export function DeleteAccountScreen({
    api,
    onDeleted,
    onCancel,
}: {
    api: AccountApi;
    onDeleted: () => void;
    onCancel: () => void;
}) {
    const [typed, setTyped] = useState('');
    const [error, setError] = useState<string>();
    const [deleting, setDeleting] = useState(false);
    const inFlight = useRef(false);

    const confirmed = isConfirmed(typed);

    const remove = async () => {
        if (!confirmed || inFlight.current) return;
        inFlight.current = true;
        setDeleting(true);
        setError(undefined);
        try {
            await api.deleteAccount();
            onDeleted();
        } catch (failure: unknown) {
            setError(messageOf(failure));
            setDeleting(false);
            inFlight.current = false;
        }
    };

    return (
        <View style={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Excluir minha conta
            </Text>
            <Text>
                Isto apaga de vez seu perfil, seu perfil de saúde, suas avaliações e fotos, seus treinos,
                refeições e recordes, suas restrições, sua ficha própria, seus vínculos e seu login (a menos
                que você também use o FitHub como profissional: aí o login fica, para o lado profissional).
                Não há como desfazer nem recuperar a conta.
            </Text>
            <Text>
                As fichas e os planos que seus profissionais criaram continuam com eles, sem nada que
                identifique você. O registro de quais profissionais leram seus dados de saúde, sem o conteúdo,
                fica guardado por 6 meses, como exige a lei.
            </Text>
            <Field
                label={`Para confirmar, digite ${CONFIRMATION_WORD}`}
                value={typed}
                onChangeText={setTyped}
                autoCapitalize="characters"
                autoCorrect={false}
                editable={!deleting}
            />
            {error ? <Alert>{error}</Alert> : null}
            <Button onPress={() => void remove()} disabled={!confirmed || deleting}>
                {deleting ? 'Excluindo…' : 'Excluir minha conta de vez'}
            </Button>
            <Button onPress={onCancel} disabled={deleting}>
                Cancelar
            </Button>
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 12, alignSelf: 'stretch' },
    title: { fontSize: 20, fontWeight: '600' },
});
