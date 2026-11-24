import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { ApiProblemError, type WorkoutPlan } from '../../lib/api';
import { messageOf } from '../profile/profileApi';
import { Alert, Button, Field } from '../profile/ui';
import {
    emptyForm,
    fromPlan,
    letterOf,
    MAX_DIVISIONS,
    newItem,
    toInput,
    validate,
    validateForPublish,
    type ItemForm,
    type OwnPlanApi,
    type PlanForm,
} from './ownPlanForm';

function todayText(now: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`;
}

/** A ficha própria em edição: a mais nova ainda ativa (rascunho ou publicada); a substituída não volta. */
function current(plans: WorkoutPlan[]): WorkoutPlan | undefined {
    return plans.find((plan) => plan.status !== 'REPLACED');
}

/**
 * Minha ficha (DEV-0073, FR-52): o aluno monta a ficha própria com divisões e exercícios do catálogo, com séries,
 * repetições (ou faixa), carga e descanso. Sem técnica nem alternativas no app. Salvar grava o rascunho; "Publicar"
 * pede confirmação e exige exercício em cada divisão. Na ficha publicada, salvar cria a versão seguinte, e a tela avisa.
 */
export function OwnPlanScreen({
    api,
    now = new Date(),
    onDone,
}: {
    api: OwnPlanApi;
    now?: Date;
    onDone: () => void;
}) {
    const [plan, setPlan] = useState<WorkoutPlan | null>();
    const [form, setForm] = useState<PlanForm>();
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [message, setMessage] = useState<string>();
    const [notice, setNotice] = useState<string>();
    const [saving, setSaving] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [attempt, setAttempt] = useState(0);
    // O "hoje" do formulário novo é o da abertura da tela.
    const [today] = useState(() => todayText(now));

    useEffect(() => {
        let active = true;
        const load = async () => {
            const found = current(await api.list()) ?? null;
            const ids = new Set(
                (found?.divisions ?? []).flatMap((division) =>
                    (division.items ?? []).map((item) => item.exerciseId ?? ''),
                ),
            );
            const names = new Map<string, string>();
            await Promise.all(
                [...ids].map(async (id) => {
                    const name = await api.exerciseName(id);
                    if (name) names.set(id, name);
                }),
            );
            return { found, names };
        };
        load().then(
            ({ found, names }) => {
                if (!active) return;
                setPlan(found);
                setForm(found ? fromPlan(found, names) : emptyForm(today));
            },
            (failure) => active && setMessage(messageOf(failure)),
        );
        return () => {
            active = false;
        };
    }, [api, attempt, today]);

    if (!form) {
        return (
            <View style={styles.box}>
                {message ? (
                    <>
                        <Alert>{message}</Alert>
                        <Button
                            onPress={() => {
                                setMessage(undefined);
                                setAttempt((value) => value + 1);
                            }}
                        >
                            Tentar de novo
                        </Button>
                    </>
                ) : (
                    <Text accessibilityLiveRegion="polite">Carregando sua ficha…</Text>
                )}
                <Button onPress={onDone}>Voltar</Button>
            </View>
        );
    }

    const published = plan?.status === 'PUBLISHED';
    const change = (next: PlanForm) => {
        setForm(next);
        setNotice(undefined);
        setConfirming(false);
    };
    const changeItem = (d: number, i: number, patch: Partial<ItemForm>) =>
        change({
            ...form,
            divisions: form.divisions.map((division, dd) =>
                dd === d
                    ? {
                          ...division,
                          items: division.items.map((item, ii) => (ii === i ? { ...item, ...patch } : item)),
                      }
                    : division,
            ),
        });

    /** Grava o formulário; devolve a ficha gravada, ou nada se a validação ou a API recusou. */
    const save = async (): Promise<WorkoutPlan | undefined> => {
        const local = validate(form);
        setErrors(local);
        setMessage(undefined);
        if (Object.keys(local).length > 0) {
            setMessage('Confira os campos destacados.');
            return undefined;
        }
        setSaving(true);
        try {
            const body = toInput(form, published ? (plan?.version ?? null) : null);
            const saved = plan?.id ? await api.update(plan.id, body) : await api.create(body);
            setPlan(saved);
            return saved;
        } catch (failure) {
            // A regra é da API: o campo recusado aparece no item, e o digitado fica.
            if (failure instanceof ApiProblemError && Object.keys(failure.fieldErrors).length > 0) {
                setErrors(failure.fieldErrors);
                setMessage('Confira os campos destacados.');
            } else {
                setMessage(messageOf(failure));
            }
            return undefined;
        } finally {
            setSaving(false);
        }
    };

    const onSave = async () => {
        const saved = await save();
        if (saved) {
            setNotice(
                saved.status === 'PUBLISHED'
                    ? `Ficha salva: versão ${saved.version ?? ''} publicada.`
                    : 'Rascunho salvo. Publique para treinar com ela.',
            );
        }
    };

    const askPublish = () => {
        const blocking = { ...validate(form), ...validateForPublish(form) };
        setErrors(blocking);
        if (Object.keys(blocking).length > 0) {
            setMessage(
                'Para publicar, cada treino precisa de ao menos um exercício e os campos têm de estar certos.',
            );
            return;
        }
        setMessage(undefined);
        setConfirming(true);
    };

    const publish = async () => {
        setConfirming(false);
        const saved = await save();
        if (!saved?.id) return;
        setSaving(true);
        try {
            setPlan(await api.publish(saved.id));
            setNotice('Ficha publicada: ela já aparece no Treino de hoje.');
        } catch (failure) {
            if (failure instanceof ApiProblemError && Object.keys(failure.fieldErrors).length > 0) {
                setErrors(failure.fieldErrors);
            }
            setMessage(messageOf(failure));
        } finally {
            setSaving(false);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.box}>
            <Text accessibilityRole="header" style={styles.title}>
                Minha ficha
            </Text>
            {published ? (
                <Text accessibilityRole="alert" style={styles.warning}>
                    Esta ficha está publicada: salvar gera a versão {(plan?.version ?? 0) + 1}, e os próximos
                    treinos usam a nova.
                </Text>
            ) : null}
            {message ? <Alert>{message}</Alert> : null}
            {notice ? <Text accessibilityLiveRegion="polite">{notice}</Text> : null}
            <Field
                label="Nome da ficha"
                value={form.name}
                onChangeText={(name) => change({ ...form, name })}
                error={errors.name}
            />
            <Field
                label="Início (dd/mm/aaaa)"
                value={form.startsOn}
                onChangeText={(startsOn) => change({ ...form, startsOn })}
                error={errors.startsOn}
            />
            <Field
                label="Validade (semanas)"
                value={form.validityWeeks}
                onChangeText={(validityWeeks) => change({ ...form, validityWeeks })}
                keyboardType="number-pad"
                error={errors.validityWeeks}
            />
            {errors.divisions ? <Alert>{errors.divisions}</Alert> : null}
            {form.divisions.map((division, d) => (
                <DivisionEditor
                    key={d}
                    api={api}
                    position={d}
                    name={division.name}
                    items={division.items}
                    errors={errors}
                    canRemove={form.divisions.length > 1}
                    onName={(name) =>
                        change({
                            ...form,
                            divisions: form.divisions.map((each, dd) =>
                                dd === d ? { ...each, name } : each,
                            ),
                        })
                    }
                    onAdd={(exercise) =>
                        change({
                            ...form,
                            divisions: form.divisions.map((each, dd) =>
                                dd === d ? { ...each, items: [...each.items, newItem(exercise)] } : each,
                            ),
                        })
                    }
                    onItem={(i, patch) => changeItem(d, i, patch)}
                    onRemoveItem={(i) =>
                        change({
                            ...form,
                            divisions: form.divisions.map((each, dd) =>
                                dd === d ? { ...each, items: each.items.filter((_, ii) => ii !== i) } : each,
                            ),
                        })
                    }
                    onRemove={() =>
                        change({ ...form, divisions: form.divisions.filter((_, dd) => dd !== d) })
                    }
                />
            ))}
            {form.divisions.length < MAX_DIVISIONS ? (
                <Button
                    onPress={() =>
                        change({ ...form, divisions: [...form.divisions, { name: '', items: [] }] })
                    }
                >
                    {`Adicionar treino ${letterOf(form.divisions.length)}`}
                </Button>
            ) : null}
            <Button disabled={saving} onPress={() => void onSave()}>
                {saving ? 'Salvando…' : 'Salvar ficha'}
            </Button>
            {published ? null : confirming ? (
                <View style={styles.confirm} accessibilityLabel="Confirmar publicação">
                    <Text>Publicar a ficha? Ela passa a aparecer no Treino de hoje.</Text>
                    <Button disabled={saving} onPress={() => void publish()}>
                        Confirmar publicação
                    </Button>
                    <Button onPress={() => setConfirming(false)}>Cancelar</Button>
                </View>
            ) : (
                <Button disabled={saving} onPress={askPublish}>
                    Publicar
                </Button>
            )}
            <Button onPress={onDone}>Voltar</Button>
        </ScrollView>
    );
}

function DivisionEditor({
    api,
    position,
    name,
    items,
    errors,
    canRemove,
    onName,
    onAdd,
    onItem,
    onRemoveItem,
    onRemove,
}: {
    api: OwnPlanApi;
    position: number;
    name: string;
    items: ItemForm[];
    errors: Record<string, string>;
    canRemove: boolean;
    onName: (name: string) => void;
    onAdd: (exercise: { id?: string; name?: string }) => void;
    onItem: (index: number, patch: Partial<ItemForm>) => void;
    onRemoveItem: (index: number) => void;
    onRemove: () => void;
}) {
    const letter = letterOf(position);
    const path = `divisions[${position}]`;
    const [query, setQuery] = useState('');
    const [found, setFound] = useState<{ id?: string; name?: string }[]>([]);
    const [searchError, setSearchError] = useState<string>();

    const search = async () => {
        setSearchError(undefined);
        try {
            setFound(await api.searchExercises(query.trim()));
        } catch (failure) {
            setSearchError(messageOf(failure));
        }
    };

    return (
        <View style={styles.card} accessibilityLabel={`Treino ${letter}`}>
            <Text style={styles.strong}>Treino {letter}</Text>
            <Field
                label={`Nome do treino ${letter}`}
                value={name}
                onChangeText={onName}
                error={errors[`${path}.name`]}
            />
            {errors[`${path}.items`] ? <Alert>{errors[`${path}.items`]}</Alert> : null}
            {items.map((item, i) => {
                const itemPath = `${path}.items[${i}]`;
                const label = `${item.exerciseName} (treino ${letter})`;
                return (
                    <View key={`${item.exerciseId}-${i}`} style={styles.item}>
                        <Text style={styles.strong}>{item.exerciseName}</Text>
                        {errors[`${itemPath}.exerciseId`] ? (
                            <Alert>{errors[`${itemPath}.exerciseId`]}</Alert>
                        ) : null}
                        <Field
                            label={`Séries de ${label}`}
                            value={item.sets}
                            onChangeText={(sets) => onItem(i, { sets })}
                            keyboardType="number-pad"
                            error={errors[`${itemPath}.sets`]}
                        />
                        <Field
                            label={`Repetições de ${label}`}
                            value={item.repsMin}
                            onChangeText={(repsMin) => onItem(i, { repsMin })}
                            keyboardType="number-pad"
                            error={errors[`${itemPath}.repsMin`]}
                        />
                        <Field
                            label={`Até (faixa, opcional) de ${label}`}
                            value={item.repsMax}
                            onChangeText={(repsMax) => onItem(i, { repsMax })}
                            keyboardType="number-pad"
                            error={errors[`${itemPath}.repsMax`]}
                        />
                        <Field
                            label={`Carga (kg, opcional) de ${label}`}
                            value={item.loadKg}
                            onChangeText={(loadKg) => onItem(i, { loadKg })}
                            keyboardType="decimal-pad"
                            error={errors[`${itemPath}.loadKg`]}
                        />
                        <Field
                            label={`Descanso (s, opcional) de ${label}`}
                            value={item.restSeconds}
                            onChangeText={(restSeconds) => onItem(i, { restSeconds })}
                            keyboardType="number-pad"
                            error={errors[`${itemPath}.restSeconds`]}
                        />
                        <Button onPress={() => onRemoveItem(i)}>{`Remover ${label}`}</Button>
                    </View>
                );
            })}
            <Field label={`Buscar exercício para o treino ${letter}`} value={query} onChangeText={setQuery} />
            {searchError ? <Alert>{searchError}</Alert> : null}
            <Button disabled={query.trim().length < 2} onPress={() => void search()}>
                {`Buscar para o treino ${letter}`}
            </Button>
            {found.map((option) => (
                <Button
                    key={option.id}
                    onPress={() => {
                        onAdd(option);
                        setFound([]);
                        setQuery('');
                    }}
                >
                    {`Adicionar ${option.name ?? ''} ao treino ${letter}`}
                </Button>
            ))}
            {canRemove ? <Button onPress={onRemove}>{`Remover treino ${letter}`}</Button> : null}
        </View>
    );
}

const styles = StyleSheet.create({
    box: { gap: 10, alignItems: 'stretch', alignSelf: 'stretch', padding: 4 },
    title: { fontSize: 18, fontWeight: '600', textAlign: 'center' },
    warning: { color: '#92400e' },
    card: { gap: 6, borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10 },
    item: { gap: 4, paddingVertical: 4 },
    strong: { fontWeight: '600' },
    confirm: { gap: 6 },
});
