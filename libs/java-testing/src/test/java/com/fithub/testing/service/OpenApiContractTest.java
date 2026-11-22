package com.fithub.testing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiContractTest {

    @TempDir
    Path dir;

    @Test
    void normalizationIgnoresKeyOrderAndLineEndings() {
        String a = OpenApiContract.normalize("{\"b\":1,\"a\":{\"d\":[2,1],\"c\":true}}");
        String b = OpenApiContract.normalize("{\r\n\"a\":{\"c\":true,\"d\":[2,1]},\r\n\"b\":1}");
        assertThat(a).isEqualTo(b).doesNotContain("\r").endsWith("}\n").startsWith("{\n  \"a\"");
        // A ordem dos itens de lista é contrato: não é reordenada.
        assertThat(OpenApiContract.normalize("{\"d\":[1,2]}")).isNotEqualTo(OpenApiContract.normalize("{\"d\":[2,1]}"));
    }

    @Test
    void outdatedOrMissingContractFailsWithTheCommandsToFixIt() throws IOException {
        Path file = dir.resolve("openapi/account-service.json");
        assertThatThrownBy(() -> OpenApiContract.verify(file, "{\"openapi\":\"3.1.0\"}", false))
                .hasMessageContaining("Contrato OpenAPI ausente").hasMessageContaining("fithub.openapi.update=true");

        Files.createDirectories(file.getParent());
        Files.writeString(file, OpenApiContract.normalize("{\"openapi\":\"3.0.1\"}"));
        assertThatThrownBy(() -> OpenApiContract.verify(file, "{\"openapi\":\"3.1.0\"}", false))
                .hasMessageContaining("Contrato OpenAPI desatualizado")
                .hasMessageContaining("pnpm --filter @fithub/api-types generate");
    }

    @Test
    void updateRewritesTheContractAndAnEqualContractPasses() throws IOException {
        Path file = dir.resolve("openapi/client-service.json");
        OpenApiContract.verify(file, "{\"paths\":{},\"openapi\":\"3.1.0\"}", true);
        assertThat(Files.readString(file)).isEqualTo("{\n  \"openapi\" : \"3.1.0\",\n  \"paths\" : { }\n}\n");

        // Mesmo conteúdo, com CRLF (checkout no Windows) e outra ordem de chaves: passa.
        Files.writeString(file, Files.readString(file).replace("\n", "\r\n"));
        OpenApiContract.verify(file, "{\"openapi\":\"3.1.0\",\"paths\":{}}", false);
    }

    @Test
    void repositoryRootIsFoundFromAModuleFolder() throws IOException {
        Files.writeString(dir.resolve("pnpm-workspace.yaml"), "packages: []\n");
        Files.writeString(dir.resolve("pom.xml"), "<project/>\n");
        Path module = Files.createDirectories(dir.resolve("services/account-service"));
        assertThat(OpenApiContract.repositoryRoot(module)).isEqualTo(dir);
        assertThatThrownBy(() -> OpenApiContract.repositoryRoot(Files.createTempDirectory("sem-raiz")))
                .isInstanceOf(IllegalStateException.class);
    }
}
