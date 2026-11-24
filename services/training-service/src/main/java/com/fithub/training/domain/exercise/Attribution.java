package com.fithub.training.domain.exercise;

/**
 * Origem e licença de um exercício importado de uma base externa (ADR-010): a tela mostra a atribuição, e o id
 * externo deixa a importação atualizar sem duplicar (DEV-0054).
 *
 * <p>A mídia importada tem licença e autor próprios, muitas vezes diferentes dos do texto: o crédito dela vem em
 * {@code mediaLicense} e {@code mediaAuthor}, juntos ou nenhum (DEV-0054).
 *
 * @param source nome da base externa, por exemplo {@code wger}
 * @param externalId id do exercício na base externa
 * @param license licença do texto, por exemplo {@code CC-BY-SA 4}
 * @param author autor do texto, a quem a licença manda dar crédito
 * @param mediaLicense licença da mídia importada, ou {@code null} quando o item não tem mídia
 * @param mediaAuthor autor da mídia importada, ou {@code null} quando o item não tem mídia
 */
public record Attribution(
        String source, String externalId, String license, String author, String mediaLicense, String mediaAuthor) {

    private static final int MAX_LENGTH = 200;

    public Attribution {
        source = required("attribution.source", source);
        externalId = required("attribution.externalId", externalId);
        license = required("attribution.license", license);
        author = required("attribution.author", author);
        if ((mediaLicense == null || mediaLicense.isBlank()) != (mediaAuthor == null || mediaAuthor.isBlank())) {
            throw new InvalidExerciseException(
                    "attribution.mediaLicense", "O crédito da mídia precisa de licença e autor juntos.");
        }
        mediaLicense = mediaLicense == null || mediaLicense.isBlank()
                ? null
                : required("attribution.mediaLicense", mediaLicense);
        mediaAuthor =
                mediaAuthor == null || mediaAuthor.isBlank() ? null : required("attribution.mediaAuthor", mediaAuthor);
    }

    /** Item importado sem mídia. */
    public Attribution(String source, String externalId, String license, String author) {
        this(source, externalId, license, author, null, null);
    }

    /** Se a mídia do item tem crédito registrado. */
    public boolean hasMediaCredit() {
        return mediaLicense != null;
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_LENGTH) {
            throw new InvalidExerciseException(
                    field, "A atribuição precisa de fonte, id, licença e autor, com até 200 caracteres cada.");
        }
        return value.strip();
    }
}
