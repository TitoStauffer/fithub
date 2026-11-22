package com.fithub.testing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato OpenAPI versionado de cada serviço (DEV-0022): o JSON em
 * {@code packages/api-types/openapi/<servico>.json} é a fonte dos tipos TypeScript dos apps. O teste de
 * esqueleto compara o OpenAPI vivo com esse arquivo e falha se ele estiver desatualizado; com
 * {@code -Dfithub.openapi.update=true}, regrava o arquivo em vez de comparar.
 *
 * <p>O JSON é normalizado (chaves em ordem alfabética, recuo de 2 espaços, LF e quebra final) para que
 * o diff mostre só mudança de contrato, nunca de ordem.
 */
public final class OpenApiContract {

    static final String UPDATE_PROPERTY = "fithub.openapi.update";

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private OpenApiContract() {
    }

    /** JSON normalizado: mesma entrada em qualquer ordem de chaves produz o mesmo texto. */
    public static String normalize(String json) {
        Object tree = JSON.readValue(json, Object.class);
        return JSON.writeValueAsString(tree).replace("\r\n", "\n") + "\n";
    }

    /** Arquivo do contrato do serviço, a partir da raiz do monorepo. */
    public static Path contractFile(String serviceName) {
        return repositoryRoot(Path.of("").toAbsolutePath()).resolve("packages/api-types/openapi/" + serviceName + ".json");
    }

    /** Compara (ou regrava, com a propriedade de atualização) o contrato versionado com o OpenAPI vivo. */
    public static void verify(String serviceName, String liveJson) {
        verify(contractFile(serviceName), liveJson, Boolean.getBoolean(UPDATE_PROPERTY));
    }

    static void verify(Path file, String liveJson, boolean update) {
        String live = normalize(liveJson);
        try {
            if (update) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, live, StandardCharsets.UTF_8);
                return;
            }
            String hint = "O OpenAPI do serviço mudou. Regrave o contrato com "
                    + "`mvn -pl services/" + serviceNameOf(file) + " -am test -Dtest=*SkeletonTest "
                    + "-Dsurefire.failIfNoSpecifiedTests=false -D" + UPDATE_PROPERTY + "=true` e gere os tipos com "
                    + "`pnpm --filter @fithub/api-types generate` (packages/api-types/README.md).";
            assertThat(file).as("Contrato OpenAPI ausente: %s. %s", file, hint).exists();
            // O checkout no Windows pode trazer CRLF (core.autocrlf); o contrato é o mesmo.
            String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertThat(committed).as("Contrato OpenAPI desatualizado: %s. %s", file, hint).isEqualTo(live);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String serviceNameOf(Path contract) {
        return contract.getFileName().toString().replaceFirst("\\.json$", "");
    }

    /** Sobe a partir da pasta do módulo até a raiz do monorepo (onde está o pnpm-workspace.yaml). */
    static Path repositoryRoot(Path start) {
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("pnpm-workspace.yaml")) && Files.exists(dir.resolve("pom.xml"))) return dir;
        }
        throw new IllegalStateException("Raiz do monorepo (pnpm-workspace.yaml e pom.xml) não encontrada a partir de " + start);
    }
}
