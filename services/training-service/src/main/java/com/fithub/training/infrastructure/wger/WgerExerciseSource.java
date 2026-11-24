package com.fithub.training.infrastructure.wger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fithub.training.application.ExerciseCatalogSource;
import com.fithub.training.application.ExerciseSourceUnavailableException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lê a base pública do wger ({@code /exerciseinfo/}, paginada) para a importação (DEV-0054, ADR-010).
 *
 * <p>A paginação usa {@code limit} e {@code offset} calculados aqui, não o link {@code next} da resposta: o
 * serviço só chama o endereço configurado. O nome vem da tradução em português, ou em inglês quando não houver.
 */
@Component
class WgerExerciseSource implements ExerciseCatalogSource {

    static final String SOURCE = "wger";

    private final RestClient http;
    private final WgerSettings settings;

    // Montado aqui, como no account-service: o RestClient.Builder do Spring Boot 4 pede um módulo a mais.
    @Autowired
    WgerExerciseSource(WgerSettings settings) {
        this(
                RestClient.builder()
                        .requestFactory(timeouts(settings))
                        .baseUrl(settings.baseUrl())
                        .build(),
                settings);
    }

    WgerExerciseSource(RestClient http, WgerSettings settings) {
        this.http = http;
        this.settings = settings;
    }

    private static JdkClientHttpRequestFactory timeouts(WgerSettings settings) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
        factory.setReadTimeout(settings.timeout());
        return factory;
    }

    @Override
    public String name() {
        return SOURCE;
    }

    @Override
    public void forEach(Consumer<Entry> consumer) {
        Map<Long, String> licenses = licenses();
        int offset = 0;
        for (int page = 0; ; page++) {
            if (page >= settings.maxPages()) {
                throw new ExerciseSourceUnavailableException("A paginação do wger passou do limite configurado.");
            }
            ItemsPage items =
                    get(ItemsPage.class, "/exerciseinfo/?limit={limit}&offset={offset}", settings.pageSize(), offset);
            if (items.results() == null) {
                throw unexpectedFormat();
            }
            for (Item item : items.results()) {
                if (item != null) {
                    consumer.accept(toEntry(item, licenses));
                }
            }
            offset += items.results().size();
            if (items.results().isEmpty() || items.count() == null || offset >= items.count()) {
                return;
            }
        }
    }

    /** Nome curto de cada licença pelo id, que é o que a tradução e a mídia trazem. */
    private Map<Long, String> licenses() {
        LicensesPage page = get(LicensesPage.class, "/license/?limit={limit}&offset={offset}", 100, 0);
        if (page.results() == null) {
            throw unexpectedFormat();
        }
        return page.results().stream()
                .filter(license -> license != null && license.id() != null && license.shortName() != null)
                .collect(Collectors.toMap(License::id, License::shortName, (first, second) -> first));
    }

    private <P> P get(Class<P> type, String uri, Object... variables) {
        P page;
        try {
            page = http.get().uri(uri, variables).retrieve().body(type);
        } catch (RestClientException failure) {
            // Só o tipo do erro: a mensagem pode trazer o corpo da resposta.
            throw new ExerciseSourceUnavailableException(
                    "O wger falhou: " + failure.getClass().getSimpleName(), failure);
        }
        if (page == null) {
            throw unexpectedFormat();
        }
        return page;
    }

    private static ExerciseSourceUnavailableException unexpectedFormat() {
        return new ExerciseSourceUnavailableException("O wger respondeu fora do formato esperado.");
    }

    static Entry toEntry(Item item, Map<Long, String> licenses) {
        Translation translation = translation(item.translations());
        String license = null;
        String author = null;
        // O texto é da tradução: o crédito é o dela. Sem ele completo, vale o do exercício.
        if (translation != null && notBlank(licenses.get(translation.license())) && notBlank(translation.author())) {
            license = licenses.get(translation.license());
            author = translation.author();
        } else if (item.license() != null) {
            license = item.license().shortName();
            author = item.author();
        }
        return new Entry(
                item.id() == null ? null : item.id().toString(),
                translation == null ? null : translation.name(),
                WgerMapping.muscleGroups(
                        ids(item.muscles()),
                        item.category() == null ? null : item.category().id()),
                WgerMapping.equipment(ids(item.equipment())),
                translation == null ? null : WgerMapping.plainText(translation.description()),
                license,
                author,
                media(item, licenses));
    }

    /** Português; sem ele (ou sem nome), inglês. */
    private static Translation translation(List<Translation> translations) {
        if (translations == null) {
            return null;
        }
        for (long language : List.of(WgerMapping.PORTUGUESE, WgerMapping.ENGLISH)) {
            for (Translation translation : translations) {
                if (translation != null
                        && Objects.equals(translation.language(), language)
                        && notBlank(translation.name())) {
                    return translation;
                }
            }
        }
        return null;
    }

    /** Imagens antes de vídeos, a principal de cada um primeiro. */
    private static List<Media> media(Item item, Map<Long, String> licenses) {
        Comparator<MediaFile> mainFirst = Comparator.comparing(file -> !Boolean.TRUE.equals(file.main()));
        List<Media> media = new ArrayList<>();
        Stream.of(item.images(), item.videos())
                .map(files -> files == null ? List.<MediaFile>of() : files)
                .forEach(files -> files.stream()
                        .filter(Objects::nonNull)
                        .sorted(mainFirst)
                        .forEach(file -> media.add(new Media(
                                file.image() != null ? file.image() : file.video(),
                                licenses.get(file.license()),
                                file.author()))));
        return media;
    }

    private static List<Long> ids(List<Ref> refs) {
        return refs == null
                ? List.of()
                : refs.stream()
                        .filter(Objects::nonNull)
                        .map(Ref::id)
                        .filter(Objects::nonNull)
                        .toList();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    // Formato da API pública do wger; campos desconhecidos são ignorados.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemsPage(Integer count, List<Item> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LicensesPage(Integer count, List<License> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(
            Long id,
            Ref category,
            List<Ref> muscles,
            List<Ref> equipment,
            License license,
            @JsonProperty("license_author") String author,
            List<Translation> translations,
            List<MediaFile> images,
            List<MediaFile> videos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ref(Long id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record License(Long id, @JsonProperty("short_name") String shortName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(
            String name,
            String description,
            Long language,
            Long license,
            @JsonProperty("license_author") String author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaFile(
            String image,
            String video,
            @JsonProperty("is_main") Boolean main,
            Long license,
            @JsonProperty("license_author") String author) {}
}
