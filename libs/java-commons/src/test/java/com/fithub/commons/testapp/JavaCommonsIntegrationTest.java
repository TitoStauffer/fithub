package com.fithub.commons.testapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Sem banco nem broker: as peças de mensageria (DEV-0014) têm o próprio teste com Testcontainers.
@SpringBootTest(
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class JavaCommonsIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    // --- Resource server JWT ---

    @Test
    void requestWithoutTokenIsRejectedAsProblemDetails() throws Exception {
        mvc.perform(get("/secure"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void invalidTokenIsRejectedWithoutExplainingWhy() throws Exception {
        mvc.perform(get("/secure").header("Authorization", "Bearer token-forjado"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Envie um token de acesso válido para usar este recurso."));
    }

    @Test
    void validTokenReachesTheEndpointWithTheSubjectAsPrincipal() throws Exception {
        mvc.perform(get("/secure").header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("usuario-cliente"));
    }

    @Test
    void realmRoleFromTheTokenAuthorizesTheProtectedEndpoint() throws Exception {
        mvc.perform(get("/admin").header("Authorization", "Bearer " + TestApplication.ADMIN_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithoutTheRequiredRoleIsForbiddenAsProblemDetails() throws Exception {
        mvc.perform(get("/admin").header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void healthIsPublicByDefault() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // --- Problem Details ---

    @Test
    void unexpectedErrorBecomesGenericProblemWithoutLeakingTheMessage() throws Exception {
        String body = mvc.perform(get("/boom").header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("detalhe interno").doesNotContain("IllegalStateException");
    }

    @Test
    void validationErrorListsTheInvalidFields() throws Exception {
        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("é obrigatório"));
    }

    @Test
    void frameworkErrorsKeepTheProblemDetailsFormat() throws Exception {
        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- Logging JSON com traceId ---

    @Test
    void requestLogLineIsJsonWithTraceId(CapturedOutput output) throws Exception {
        mvc.perform(get("/log").header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN))
                .andExpect(status().isOk());
        String line = output.getOut()
                .lines()
                .filter(candidate -> candidate.contains("linha de log de teste da java-commons"))
                .findFirst()
                .orElseThrow();
        assertThat(line).startsWith("{").endsWith("}");
        assertThat(line).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
    }

    // --- Observações exportadas como spans (DEV-0109) ---

    @Autowired
    private ObservationNames observationNames;

    @Test
    void authenticatedRequestIsObservedWithoutTheSpringSecuritySpans() throws Exception {
        observationNames.names.clear();
        mvc.perform(get("/secure").header("Authorization", "Bearer " + TestApplication.CLIENT_TOKEN))
                .andExpect(status().isOk());

        assertThat(observationNames.names).contains("http.server.requests");
        // Os spans do Spring Security levam os papéis da pessoa: ficam desligados pela java-commons.
        assertThat(observationNames.names).noneMatch(name -> name.startsWith("spring.security"));
    }

    /** Guarda o nome de cada observação iniciada: cada uma vira um span quando o exportador está ligado. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationNames implements ObservationHandler<Observation.Context> {

        final Set<String> names = ConcurrentHashMap.newKeySet();

        @Override
        public void onStart(Observation.Context context) {
            names.add(context.getName());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
