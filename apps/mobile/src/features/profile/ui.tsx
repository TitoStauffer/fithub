import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';

/** Campo de texto com rótulo e a mensagem de erro logo abaixo, ligada para leitores de tela. */
export function Field({
    label,
    error,
    ...input
}: { label: string; error?: string } & Omit<TextInputProps, 'accessibilityLabel'>) {
    return (
        <View style={styles.field}>
            <Text style={styles.label}>{label}</Text>
            <TextInput
                accessibilityLabel={label}
                accessibilityHint={error}
                style={[styles.input, error ? styles.inputError : null]}
                {...input}
            />
            {error ? <Text style={styles.error}>{error}</Text> : null}
        </View>
    );
}

/** Grupo de opções de escolha única ou múltipla, como botões selecionáveis. */
export function Choices<T extends string>({
    label,
    options,
    selected,
    onToggle,
    error,
}: {
    label: string;
    options: { value: T; label: string }[];
    selected: readonly T[];
    onToggle: (value: T) => void;
    error?: string;
}) {
    return (
        <View style={styles.field} accessibilityLabel={label}>
            <Text style={styles.label}>{label}</Text>
            <View style={styles.choices}>
                {options.map((option) => {
                    const checked = selected.includes(option.value);
                    return (
                        <Pressable
                            key={option.value}
                            accessibilityRole="checkbox"
                            accessibilityState={{ checked }}
                            accessibilityLabel={option.label}
                            onPress={() => onToggle(option.value)}
                            style={[styles.choice, checked ? styles.choiceChecked : null]}
                        >
                            <Text>{option.label}</Text>
                        </Pressable>
                    );
                })}
            </View>
            {error ? <Text style={styles.error}>{error}</Text> : null}
        </View>
    );
}

export function Button({
    children,
    onPress,
    disabled,
}: {
    children: ReactNode;
    onPress: () => void;
    disabled?: boolean;
}) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: Boolean(disabled) }}
            disabled={disabled}
            onPress={onPress}
            style={[styles.button, disabled ? styles.buttonDisabled : null]}
        >
            <Text>{children}</Text>
        </Pressable>
    );
}

export function Alert({ children }: { children: ReactNode }) {
    return (
        <Text accessibilityRole="alert" style={styles.error}>
            {children}
        </Text>
    );
}

const styles = StyleSheet.create({
    field: { gap: 4, alignSelf: 'stretch' },
    label: { fontWeight: '600' },
    input: {
        borderWidth: 1,
        borderColor: '#ccc',
        borderRadius: 6,
        paddingHorizontal: 10,
        paddingVertical: 6,
    },
    inputError: { borderColor: '#b91c1c' },
    error: { color: '#b91c1c' },
    choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
    choice: {
        borderWidth: 1,
        borderColor: '#ccc',
        borderRadius: 16,
        paddingHorizontal: 10,
        paddingVertical: 4,
    },
    choiceChecked: { backgroundColor: '#dbeafe', borderColor: '#2563eb' },
    button: {
        borderWidth: 1,
        borderColor: '#ccc',
        borderRadius: 6,
        paddingHorizontal: 12,
        paddingVertical: 6,
        alignItems: 'center',
    },
    buttonDisabled: { opacity: 0.5 },
});
