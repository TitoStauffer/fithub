package com.fithub.training.application;

import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Base externa de exercícios que a importação copia para o catálogo local (DEV-0054, ADR-010). */
public interface ExerciseCatalogSource {

    /** Nome da base, gravado como fonte de cada exercício importado, por exemplo {@code wger}. */
    String name();

    /**
     * Entrega cada item da base, na ordem em que ela pagina.
     *
     * @throws ExerciseSourceUnavailableException quando a base falha (erro HTTP, tempo esgotado, resposta fora do
     *     formato); os itens já entregues continuam válidos
     */
    void forEach(Consumer<Entry> consumer);

    /**
     * Um exercício como a base o descreve, já traduzido para os grupos e equipamentos do FitHub. Os campos podem
     * vir vazios ou inválidos: a importação pula o item que o domínio recusar.
     *
     * @param license licença do texto
     * @param author autor do texto
     * @param media imagens e vídeos do item, na ordem de preferência
     */
    record Entry(
            String externalId,
            String name,
            Set<MuscleGroup> muscleGroups,
            Equipment equipment,
            String instructions,
            String license,
            String author,
            List<Media> media) {}

    /**
     * Imagem ou vídeo do item, com a licença e o autor registrados nele.
     *
     * @param license nome curto da licença, por exemplo {@code CC-BY-SA 4}, ou {@code null} se não houver
     */
    record Media(String url, String license, String author) {}
}
