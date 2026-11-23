package com.fithub.training.domain.exercise;

/**
 * Origem e licença de um exercício importado de uma base externa (ADR-010): a tela mostra a atribuição, e o id
 * externo deixa a importação atualizar sem duplicar (DEV-0054).
 *
 * @param source nome da base externa, por exemplo {@code wger}
 * @param externalId id do exercício na base externa
 * @param license licença do item, por exemplo {@code CC-BY-SA 4.0}
 * @param author autor a quem a licença manda dar crédito
 */
public record Attribution(String source, String externalId, String license, String author) {

    private static final int MAX_LENGTH = 200;

    public Attribution {
        source = required("attribution.source", source);
        externalId = required("attribution.externalId", externalId);
        license = required("attribution.license", license);
        author = required("attribution.author", author);
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_LENGTH) {
            throw new InvalidExerciseException(
                    field, "A atribuição precisa de fonte, id, licença e autor, com até 200 caracteres cada.");
        }
        return value.strip();
    }
}
