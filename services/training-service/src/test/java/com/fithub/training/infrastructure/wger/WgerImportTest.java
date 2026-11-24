package com.fithub.training.infrastructure.wger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fithub.testing.service.ServiceIntegrationTest;
import com.fithub.training.application.ExerciseSourceUnavailableException;
import com.fithub.training.application.ImportExercises;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Importação da base do wger (DEV-0054) contra um servidor HTTP falso do próprio JDK, sem rede externa, e com o
 * Postgres real: paginação, tradução, licença da mídia, item inválido, reexecução e falha da fonte.
 */
class WgerImportTest extends ServiceIntegrationTest {

    private static final Instant FIRST_RUN = Instant.parse("2026-11-24T10:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-11-25T10:00:00Z");

    private static final String LICENSES = """
            {"count": 3, "results": [
              {"id": 1, "short_name": "CC-BY-SA 3"},
              {"id": 2, "short_name": "CC-BY-SA 4"},
              {"id": 5, "short_name": "ODbL"}]}
            """;

    @Autowired
    ExerciseRepository exercises;

    private HttpServer server;

    /** Resposta de cada offset do {@code /exerciseinfo/}; o status 200 é o padrão. */
    private final Map<Integer, String> pages = new ConcurrentHashMap<>();

    private final Map<Integer, Integer> statusByOffset = new ConcurrentHashMap<>();
    private volatile Duration delay = Duration.ZERO;

    /** Ids externos únicos por teste: o banco é compartilhado entre as classes de teste. */
    private long base;

    @BeforeEach
    void startServer() throws IOException {
        base = ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000L) * 10;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v2/license/", exchange -> respond(exchange, 200, LICENSES));
        server.createContext("/api/v2/exerciseinfo/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            int offset = Integer.parseInt(query.replaceAll(".*offset=(\\d+).*", "$1"));
            if (!delay.isZero()) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            respond(
                    exchange,
                    statusByOffset.getOrDefault(offset, 200),
                    pages.getOrDefault(offset, "{\"count\": 0, \"results\": []}"));
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ImportExercises importer(Instant now) {
        WgerSettings settings = new WgerSettings(
                false, "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2", 2, 10, Duration.ofSeconds(1));
        return new ImportExercises(exercises, new WgerExerciseSource(settings), Clock.fixed(now, ZoneOffset.UTC));
    }

    private String id(int n) {
        return Long.toString(base + n);
    }

    private Exercise imported(int n) {
        return exercises.findByExternalId("wger", id(n)).orElseThrow();
    }

    /** Um item do {@code /exerciseinfo/} com tradução em português e inglês, ou só em inglês. */
    private String item(int n, String portuguese, String english, String muscles, String equipment, String images) {
        StringBuilder translations = new StringBuilder();
        if (portuguese != null) {
            translations.append("""
                    {"name": "%s", "description": "<p>Desça a barra &amp; suba.</p>", "language": 7,
                     "license": 2, "license_author": "tradutora"},
                    """.formatted(portuguese));
        }
        translations.append("""
                {"name": "%s", "description": "<p>Lower the bar.</p>", "language": 2, "license": 1,
                 "license_author": "author-en"}
                """.formatted(english));
        return """
                {"id": %s, "category": {"id": 11}, "muscles": [%s], "equipment": [%s],
                 "license": {"id": 1, "short_name": "CC-BY-SA 3"}, "license_author": "wger.de",
                 "translations": [%s], "images": [%s], "videos": []}
                """.formatted(id(n), muscles, equipment, translations, images);
    }

    private static String page(int count, String... items) {
        return "{\"count\": %d, \"results\": [%s]}".formatted(count, String.join(",", items));
    }

    private static String muscle(long id) {
        return "{\"id\": %d}".formatted(id);
    }

    @Test
    void importaTodasAsPaginasComATraducaoEmPortuguesQuandoHa() {
        pages.put(
                0,
                page(
                        3,
                        item(
                                1,
                                "Supino reto",
                                "Bench press",
                                muscle(4) + "," + muscle(5),
                                "{\"id\": 1}, {\"id\": 8}",
                                ""),
                        item(2, null, "Biceps curl", muscle(1), "{\"id\": 3}", "")));
        pages.put(2, page(3, item(3, "Prancha", "Plank", "", "", "")));

        ImportExercises.Result result = importer(FIRST_RUN).run();

        assertThat(result).isEqualTo(new ImportExercises.Result(3, 0, 0, 0));
        Exercise bench = imported(1);
        assertThat(bench.kind()).isEqualTo(Exercise.Kind.CATALOG);
        assertThat(bench.name()).isEqualTo("Supino reto");
        assertThat(bench.instructions()).isEqualTo("Desça a barra & suba.");
        assertThat(bench.muscleGroups()).containsExactlyInAnyOrder(MuscleGroup.CHEST, MuscleGroup.TRICEPS);
        assertThat(bench.equipment()).isEqualTo(Equipment.BARBELL);
        // O texto é da tradução em português: o crédito é o dela.
        assertThat(bench.attribution().source()).isEqualTo("wger");
        assertThat(bench.attribution().externalId()).isEqualTo(id(1));
        assertThat(bench.attribution().license()).isEqualTo("CC-BY-SA 4");
        assertThat(bench.attribution().author()).isEqualTo("tradutora");

        Exercise curl = imported(2);
        assertThat(curl.name()).isEqualTo("Biceps curl");
        assertThat(curl.instructions()).isEqualTo("Lower the bar.");
        assertThat(curl.attribution().license()).isEqualTo("CC-BY-SA 3");
        assertThat(curl.attribution().author()).isEqualTo("author-en");
        assertThat(curl.equipment()).isEqualTo(Equipment.DUMBBELL);

        // Sem músculo mapeável, vale a categoria (peito); sem equipamento, é peso do corpo.
        Exercise plank = imported(3);
        assertThat(plank.muscleGroups()).containsExactly(MuscleGroup.CHEST);
        assertThat(plank.equipment()).isEqualTo(Equipment.BODYWEIGHT);
    }

    @Test
    void midiaSoEntraComLicencaCreativeCommonsEAutorRegistradosNela() {
        String withoutAuthor = "{\"image\": \"https://wger.de/media/1.png\", \"is_main\": true, \"license\": 2,"
                + " \"license_author\": \"\"}";
        String database = "{\"image\": \"https://wger.de/media/2.png\", \"is_main\": false, \"license\": 5,"
                + " \"license_author\": \"alguém\"}";
        String http = "{\"image\": \"http://wger.de/media/3.png\", \"is_main\": false, \"license\": 2,"
                + " \"license_author\": \"alguém\"}";
        String licensed = "{\"image\": \"https://wger.de/media/4.png\", \"is_main\": false, \"license\": 1,"
                + " \"license_author\": \"Everkinetic\"}";
        pages.put(
                0,
                page(
                        2,
                        item(
                                1,
                                "Supino reto",
                                "Bench press",
                                muscle(4),
                                "{\"id\": 1}",
                                String.join(",", withoutAuthor, database, http, licensed)),
                        item(
                                2,
                                "Rosca",
                                "Curl",
                                muscle(1),
                                "{\"id\": 3}",
                                String.join(",", withoutAuthor, database, http))));

        importer(FIRST_RUN).run();

        Exercise bench = imported(1);
        assertThat(bench.mediaUrl()).isEqualTo("https://wger.de/media/4.png");
        assertThat(bench.attribution().mediaLicense()).isEqualTo("CC-BY-SA 3");
        assertThat(bench.attribution().mediaAuthor()).isEqualTo("Everkinetic");
        // Nenhuma mídia com licença CC, autor e https: o exercício entra, sem mídia.
        Exercise curl = imported(2);
        assertThat(curl.mediaUrl()).isNull();
        assertThat(curl.attribution().hasMediaCredit()).isFalse();
    }

    @Test
    void itemSemNomeSemGrupoMuscularOuSemLicencaEPuladoEContado() {
        String noName = item(1, null, " ", muscle(4), "", "");
        // Pernas é amplo demais para virar um grupo: sem músculo mapeável, o item é pulado.
        String noGroup = item(2, "Agachamento", "Squat", muscle(99), "", "")
                .replace("\"category\": {\"id\": 11}", "\"category\": {\"id\": 9}");
        String noLicense = item(3, null, "Plank", muscle(6), "", "")
                .replace("author-en", "")
                .replace("\"license_author\": \"wger.de\"", "\"license_author\": \"\"");
        pages.put(0, page(4, noName, noGroup));
        pages.put(2, page(4, noLicense, item(4, "Remada", "Row", muscle(12), "{\"id\": 1}", "")));

        ImportExercises.Result result = importer(FIRST_RUN).run();

        assertThat(result).isEqualTo(new ImportExercises.Result(1, 0, 0, 3));
        assertThat(exercises.findByExternalId("wger", id(1))).isEmpty();
        assertThat(exercises.findByExternalId("wger", id(2))).isEmpty();
        assertThat(exercises.findByExternalId("wger", id(3))).isEmpty();
        assertThat(imported(4).name()).isEqualTo("Remada");
    }

    @Test
    void rodarDeNovoNaoDuplicaEAtualizaSoOQueMudouNaFonte() {
        pages.put(
                0,
                page(
                        2,
                        item(1, "Supino reto", "Bench press", muscle(4), "{\"id\": 1}", ""),
                        item(2, "Rosca", "Curl", muscle(1), "{\"id\": 3}", "")));
        importer(FIRST_RUN).run();
        Exercise bench = imported(1);

        pages.put(
                0,
                page(
                        2,
                        item(1, "Supino reto com barra", "Bench press", muscle(4), "{\"id\": 1}", ""),
                        item(2, "Rosca", "Curl", muscle(1), "{\"id\": 3}", "")));
        ImportExercises.Result second = importer(SECOND_RUN).run();

        assertThat(second).isEqualTo(new ImportExercises.Result(0, 1, 1, 0));
        Exercise updated = imported(1);
        assertThat(updated.id()).isEqualTo(bench.id());
        assertThat(updated.name()).isEqualTo("Supino reto com barra");
        assertThat(updated.createdAt()).isEqualTo(FIRST_RUN);
        assertThat(updated.updatedAt()).isEqualTo(SECOND_RUN);
        // O que não mudou não é regravado: nem a data de atualização muda.
        assertThat(imported(2).updatedAt()).isEqualTo(FIRST_RUN);

        // Um item que saiu da fonte continua no catálogo.
        pages.put(0, page(1, item(2, "Rosca", "Curl", muscle(1), "{\"id\": 3}", "")));
        importer(SECOND_RUN).run();
        assertThat(imported(1).name()).isEqualTo("Supino reto com barra");
    }

    @Test
    void falhaDaFonteInterrompeSemApagarNemCorromperOQueJaExiste() {
        pages.put(
                0,
                page(
                        4,
                        item(1, "Supino reto", "Bench press", muscle(4), "{\"id\": 1}", ""),
                        item(2, "Rosca", "Curl", muscle(1), "{\"id\": 3}", "")));
        pages.put(2, page(4, item(3, "Remada", "Row", muscle(12), "{\"id\": 1}", "")));
        importer(FIRST_RUN).run();
        Exercise bench = imported(1);

        // Erro HTTP no meio da paginação.
        pages.put(
                0,
                page(
                        4,
                        item(1, "Nome novo", "Bench press", muscle(4), "{\"id\": 1}", ""),
                        item(2, "Rosca", "Curl", muscle(1), "{\"id\": 3}", "")));
        statusByOffset.put(2, 500);
        assertThatThrownBy(() -> importer(SECOND_RUN).run()).isInstanceOf(ExerciseSourceUnavailableException.class);
        statusByOffset.clear();

        // JSON fora do formato.
        pages.put(2, "{\"count\": 4, \"results\": \"nada\"}");
        assertThatThrownBy(() -> importer(SECOND_RUN).run()).isInstanceOf(ExerciseSourceUnavailableException.class);
        pages.put(2, "<html>manutenção</html>");
        assertThatThrownBy(() -> importer(SECOND_RUN).run()).isInstanceOf(ExerciseSourceUnavailableException.class);
        pages.put(2, "{\"count\": 4}");
        assertThatThrownBy(() -> importer(SECOND_RUN).run()).isInstanceOf(ExerciseSourceUnavailableException.class);

        // Tempo esgotado.
        delay = Duration.ofSeconds(3);
        assertThatThrownBy(() -> importer(SECOND_RUN).run()).isInstanceOf(ExerciseSourceUnavailableException.class);
        delay = Duration.ZERO;

        // O que já estava importado continua inteiro: o item da página que falhou não foi tocado.
        Exercise row = imported(3);
        assertThat(row.name()).isEqualTo("Remada");
        assertThat(row.updatedAt()).isEqualTo(FIRST_RUN);
        assertThat(imported(1).id()).isEqualTo(bench.id());
        assertThat(exercises.findByExternalId("wger", id(1))).isPresent();
    }

    @Test
    void falhaDaFonteNoJobVaiParaOLogSemDerrubarOServico() {
        statusByOffset.put(0, 503);

        ExerciseImportJob job = new ExerciseImportJob(
                importer(FIRST_RUN), new WgerSettings(true, "http://127.0.0.1:1", 2, 10, Duration.ofSeconds(1)));

        assertThat(job.runSafely()).isFalse();
    }

    @Test
    void paginacaoSemFimParaNoLimiteConfigurado() {
        // A fonte diz ter mais itens do que entrega: sem a trava, o job pediria páginas para sempre.
        for (int offset = 0; offset <= 40; offset += 2) {
            pages.put(
                    offset,
                    page(
                            1_000_000,
                            item(offset, "Supino " + offset, "Bench", muscle(4), "", ""),
                            item(offset + 1, "Rosca " + offset, "Curl", muscle(1), "", "")));
        }

        assertThatThrownBy(() -> importer(FIRST_RUN).run())
                .isInstanceOf(ExerciseSourceUnavailableException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void mapaDeEquipamentoFicaComOMaisEspecificoETextoSaiSemHtml() {
        assertThat(WgerMapping.equipment(List.of(8L, 1L))).isEqualTo(Equipment.BARBELL);
        assertThat(WgerMapping.equipment(List.of(7L))).isEqualTo(Equipment.BODYWEIGHT);
        assertThat(WgerMapping.equipment(List.of(999L))).isEqualTo(Equipment.OTHER);
        assertThat(WgerMapping.equipment(List.of())).isEqualTo(Equipment.BODYWEIGHT);
        assertThat(WgerMapping.muscleGroups(List.of(99L), 9L)).isEmpty();
        assertThat(WgerMapping.muscleGroups(List.of(), 15L)).isEqualTo(Set.of(MuscleGroup.FULL_BODY));
        assertThat(WgerMapping.plainText("<p>Um&nbsp;</p>\n<ul><li>dois</li></ul>"))
                .isEqualTo("Um dois");
        assertThat(WgerMapping.plainText("<p></p>")).isNull();
        String longText = "a".repeat(Exercise.MAX_INSTRUCTIONS_LENGTH + 10);
        assertThat(WgerMapping.plainText(longText))
                .hasSize(Exercise.MAX_INSTRUCTIONS_LENGTH)
                .endsWith("…");
    }
}
