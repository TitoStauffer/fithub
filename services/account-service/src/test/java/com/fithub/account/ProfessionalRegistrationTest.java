package com.fithub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fithub.account.application.RegisterProfessional;
import com.fithub.account.domain.professional.Professional;
import com.fithub.account.domain.professional.ProfessionalEvents;
import com.fithub.account.domain.professional.ProfessionalRoles;
import com.fithub.eventcontracts.EventContract;
import com.fithub.eventcontracts.EventContracts;
import com.fithub.testing.service.ServiceIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cadastro do profissional ponta a ponta (DEV-0029): HTTP, validação, Postgres real e a atribuição de
 * papel, com o Keycloak trocado por um falso que registra as chamadas.
 */
@TestPropertySource(properties = "fithub.professional.role-retry.enabled=false")
class ProfessionalRegistrationTest extends ServiceIntegrationTest {

    private static final String PERSONAL_BODY = """
            {"type": "PERSONAL", "registrationNumber": "012345", "registrationCategory": "g", "registrationState": "sp"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FakeRoles roles;

    @Autowired
    RegisterProfessional registerProfessional;

    @Autowired
    FailingEvents events;

    @Autowired
    AmqpAdmin amqp;

    @Autowired
    RabbitTemplate rabbit;

    @BeforeEach
    void keycloakDisponivel() {
        roles.failing.set(false);
        events.failAfterWriting.set(false);
    }

    @TestConfiguration
    static class Fakes {

        /** Token de teste: {@code Bearer conta-<uuid>} vira um JWT com esse {@code sub}; o resto é recusado. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                // "semnome-<uuid>": conta sem nome no perfil (criada fora do cadastro do Keycloak).
                boolean withoutName = token.startsWith("semnome-");
                if (!token.startsWith("conta-") && !withoutName) {
                    throw new BadJwtException("token inválido");
                }
                var builder = Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(token.substring(token.indexOf('-') + 1));
                if (!withoutName) {
                    builder.claim("given_name", "Ana").claim("family_name", "Profissional");
                }
                return builder.claim("realm_access", Map.of("roles", List.of("client")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
            };
        }

        @Bean
        @Primary
        FakeRoles fakeRoles() {
            return new FakeRoles();
        }

        /** Grava pelo outbox de verdade e, se pedido, falha logo depois, dentro da transação. */
        @Bean
        @Primary
        FailingEvents failingEvents(@Qualifier("outboxProfessionalEvents") ProfessionalEvents outbox) {
            return new FailingEvents(outbox);
        }
    }

    static class FailingEvents implements ProfessionalEvents {

        final AtomicBoolean failAfterWriting = new AtomicBoolean();
        private final ProfessionalEvents outbox;

        FailingEvents(ProfessionalEvents outbox) {
            this.outbox = outbox;
        }

        @Override
        public void registered(Professional professional) {
            outbox.registered(professional);
            if (failAfterWriting.get()) {
                throw new IllegalStateException("falha depois de gravar o evento");
            }
        }
    }

    /** Keycloak falso: registra as atribuições e pode ficar fora do ar. */
    static class FakeRoles implements ProfessionalRoles {

        final List<String> assigned = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean failing = new AtomicBoolean();

        @Override
        public void assign(String accountId, Professional.Type type) {
            if (failing.get()) {
                throw new IllegalStateException("Keycloak fora do ar");
            }
            assigned.add(accountId + ":" + type);
        }
    }

    private static String newAccount() {
        return UUID.randomUUID().toString();
    }

    private ResultActions register(String account, String body) throws Exception {
        return mvc.perform(post("/professionals/me")
                .header("Authorization", "Bearer conta-" + account)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int rows(String table, String column, String account) {
        return jdbc.sql("select count(*) from " + table + " where " + column + " = :account")
                .param("account", account)
                .query(Integer.class)
                .single();
    }

    @Test
    void semTokenOCadastroERecusado() throws Exception {
        mvc.perform(post("/professionals/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PERSONAL_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/professionals/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/professionals/me").header("Authorization", "Bearer forjado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroValidoCriaProfissionalEOrganizacaoEAtribuiOPapel() throws Exception {
        String account = newAccount();

        register(account, PERSONAL_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/professionals/me"))
                .andExpect(jsonPath("$.type").value("PERSONAL"))
                .andExpect(jsonPath("$.registration.council").value("CREF"))
                .andExpect(jsonPath("$.registration.number").value("012345"))
                .andExpect(jsonPath("$.registration.category").value("G"))
                .andExpect(jsonPath("$.registration.state").value("SP"))
                .andExpect(jsonPath("$.registration.display").value("CREF 012345-G/SP"))
                .andExpect(jsonPath("$.situation").value("DECLARED"))
                .andExpect(jsonPath("$.roleStatus").value("ASSIGNED"));

        assertThat(roles.assigned).contains(account + ":PERSONAL");
        assertThat(rows("professional", "account_id", account)).isEqualTo(1);
        assertThat(rows("organization", "owner_account_id", account)).isEqualTo(1);
        String organizationOfProfessional = jdbc.sql(
                        "select organization_id::text from professional where account_id = :account")
                .param("account", account)
                .query(String.class)
                .single();
        String ownedOrganization = jdbc.sql("select id::text from organization where owner_account_id = :account")
                .param("account", account)
                .query(String.class)
                .single();
        assertThat(organizationOfProfessional).isEqualTo(ownedOrganization);
    }

    @Test
    void cadastroDeNutricionistaUsaOCrn() throws Exception {
        String account = newAccount();

        register(account, """
                        {"type": "NUTRITIONIST", "registrationNumber": "54321", "registrationState": "RJ"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration.council").value("CRN"))
                .andExpect(jsonPath("$.registration.display").value("CRN 54321/RJ"));
        assertThat(roles.assigned).contains(account + ":NUTRITIONIST");
    }

    @Test
    void cadaContaSoVeOProprioCadastro() throws Exception {
        String account = newAccount();
        register(account, PERSONAL_BODY).andExpect(status().isCreated());

        mvc.perform(get("/professionals/me").header("Authorization", "Bearer conta-" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.number").value("012345"));
        mvc.perform(get("/professionals/me").header("Authorization", "Bearer conta-" + newAccount()))
                .andExpect(status().isNotFound());
    }

    @Test
    void contaDoCadastroVemDoTokenENuncaDoCorpo() throws Exception {
        String account = newAccount();
        String other = newAccount();

        register(account, """
                        {"type": "NUTRITIONIST", "registrationNumber": "1", "registrationState": "SP",
                         "accountId": "%s", "roleStatus": "ASSIGNED", "situation": "VERIFIED"}
                        """.formatted(other))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situation").value("DECLARED"));

        assertThat(rows("professional", "account_id", account)).isEqualTo(1);
        assertThat(rows("professional", "account_id", other)).isZero();
    }

    @Test
    void registroForaDoFormatoVoltaComOCampoEmProblemDetails() throws Exception {
        String account = newAccount();

        register(account, """
                        {"type": "PERSONAL", "registrationNumber": "12345678", "registrationCategory": "G",
                         "registrationState": "SP"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("registrationNumber"));
        register(account, """
                        {"type": "PERSONAL", "registrationNumber": "123", "registrationState": "SP"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("registrationCategory"));
        register(account, """
                        {"type": "NUTRITIONIST", "registrationNumber": "123", "registrationCategory": "G",
                         "registrationState": "SP"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("registrationCategory"));
        register(account, """
                        {"registrationNumber": "123", "registrationState": "SP"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));

        assertThat(rows("professional", "account_id", account)).isZero();
        assertThat(roles.assigned).noneMatch(call -> call.startsWith(account));
    }

    @Test
    void pedidoRepetidoIgualDevolveOCadastroExistenteSemDuplicar() throws Exception {
        String account = newAccount();
        register(account, PERSONAL_BODY).andExpect(status().isCreated());

        register(account, PERSONAL_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.number").value("012345"));

        assertThat(rows("professional", "account_id", account)).isEqualTo(1);
        assertThat(rows("organization", "owner_account_id", account)).isEqualTo(1);
    }

    @Test
    void contaJaCadastradaComOutrosDadosRecebe409() throws Exception {
        String account = newAccount();
        register(account, PERSONAL_BODY).andExpect(status().isCreated());

        register(account, """
                        {"type": "NUTRITIONIST", "registrationNumber": "54321", "registrationState": "RJ"}
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Cadastro já existe"));

        mvc.perform(get("/professionals/me").header("Authorization", "Bearer conta-" + account))
                .andExpect(jsonPath("$.type").value("PERSONAL"));
    }

    @Test
    void pedidosSimultaneosDaMesmaContaCriamUmCadastroSo() throws Exception {
        String account = newAccount();
        int parallel = 8;
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            calls.add(() ->
                    register(account, PERSONAL_BODY).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(parallel)) {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(rows("professional", "account_id", account)).isEqualTo(1);
        assertThat(rows("organization", "owner_account_id", account)).isEqualTo(1);
    }

    @Test
    void keycloakForaDoArDeixaOPapelPendenteEARetentativaAtribuiDepois() throws Exception {
        String account = newAccount();
        roles.failing.set(true);

        register(account, PERSONAL_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleStatus").value("PENDING"));
        assertThat(roles.assigned).noneMatch(call -> call.startsWith(account));

        roles.failing.set(false);
        registerProfessional.assignPendingRoles(1000);

        assertThat(roles.assigned).contains(account + ":PERSONAL");
        mvc.perform(get("/professionals/me").header("Authorization", "Bearer conta-" + account))
                .andExpect(jsonPath("$.roleStatus").value("ASSIGNED"));
    }

    @Test
    void pedidoRepetidoComPapelPendenteTentaAtribuirDeNovo() throws Exception {
        String account = newAccount();
        roles.failing.set(true);
        register(account, PERSONAL_BODY).andExpect(jsonPath("$.roleStatus").value("PENDING"));

        roles.failing.set(false);
        register(account, PERSONAL_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleStatus").value("ASSIGNED"));
        assertThat(roles.assigned).contains(account + ":PERSONAL");
    }

    // --- evento ProfissionalCadastrado (DEV-0030) ---

    private static final String REGISTERED = "account.professional-registered.v1";

    private String professionalId(String account) {
        return jdbc.sql("select id::text from professional where account_id = :account")
                .param("account", account)
                .query(String.class)
                .single();
    }

    private List<String> outboxPayloads(String professionalId) {
        return jdbc.sql("""
                        select payload::text from fithub_outbox_event
                         where routing_key = :routingKey and payload ->> 'professionalId' = :id
                        """)
                .param("routingKey", REGISTERED)
                .param("id", professionalId)
                .query(String.class)
                .list();
    }

    @Test
    void cadastroGravaOEventoNoOutboxPeloContratoSemRegistroNemConta() throws Exception {
        String account = newAccount();
        register(account, PERSONAL_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Profissional"));

        List<String> payloads = outboxPayloads(professionalId(account));

        assertThat(payloads).hasSize(1);
        String payload = payloads.getFirst();
        assertThat(EventContracts.validate(EventContract.PROFESSIONAL_REGISTERED_V1, payload))
                .isEmpty();
        assertThat(payload)
                .contains("\"professionalType\": \"personal_trainer\"")
                .contains("\"name\": \"Ana Profissional\"")
                .doesNotContain("012345")
                .doesNotContain(account);
    }

    @Test
    void nutricionistaViraNutritionistNoEvento() throws Exception {
        String account = newAccount();
        register(account, """
                        {"type": "NUTRITIONIST", "registrationNumber": "54321", "registrationState": "RJ"}
                        """).andExpect(status().isCreated());

        assertThat(outboxPayloads(professionalId(account)))
                .singleElement()
                .satisfies(payload -> assertThat(payload).contains("\"professionalType\": \"nutritionist\""));
    }

    @Test
    void pedidoRepetidoNaoGravaOEventoDeNovo() throws Exception {
        String account = newAccount();
        register(account, PERSONAL_BODY).andExpect(status().isCreated());
        register(account, PERSONAL_BODY).andExpect(status().isOk());

        assertThat(outboxPayloads(professionalId(account))).hasSize(1);
    }

    @Test
    void pedidosSimultaneosGravamUmEventoSo() throws Exception {
        String account = newAccount();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() ->
                    register(account, PERSONAL_BODY).andReturn().getResponse().getStatus());
        }
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                result.get();
            }
        }

        assertThat(outboxPayloads(professionalId(account))).hasSize(1);
    }

    @Test
    void cadastroDesfeitoNaoDeixaNemOCadastroNemOEvento() throws Exception {
        String account = newAccount();
        events.failAfterWriting.set(true);

        register(account, PERSONAL_BODY).andExpect(status().isInternalServerError());

        assertThat(rows("professional", "account_id", account)).isZero();
        assertThat(rows("organization", "owner_account_id", account)).isZero();
        int orphanEvents = jdbc.sql("""
                        select count(*) from fithub_outbox_event
                         where routing_key = :routingKey and payload ->> 'name' = 'Ana Profissional'
                           and created_at > now() - interval '1 minute'
                           and payload ->> 'professionalId' not in (select id::text from professional)
                        """)
                .param("routingKey", REGISTERED)
                .query(Integer.class)
                .single();
        assertThat(orphanEvents).isZero();
        assertThat(roles.assigned).noneMatch(call -> call.startsWith(account));
    }

    @Test
    void contaSemNomeNoPerfilRecebe400ComOCampo() throws Exception {
        String account = newAccount();

        mvc.perform(post("/professionals/me")
                        .header("Authorization", "Bearer semnome-" + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PERSONAL_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        assertThat(rows("professional", "account_id", account)).isZero();
    }

    @Test
    void eventoChegaAoRabbitMqNaExchangeDoServicoComARoutingKeyDoContrato() throws Exception {
        // Fila só do teste, ligada à exchange do account-service antes do cadastro (o broker descarta
        // mensagem sem fila ligada). Durável porque o RabbitMQ 4 não aceita fila transitória compartilhada;
        // apagada no fim.
        Queue queue = new Queue("teste.dev0030." + UUID.randomUUID(), true, false, false);
        amqp.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange("fithub.account"))
                .with(REGISTERED);
        amqp.declareBinding(binding);
        String account = newAccount();

        register(account, PERSONAL_BODY).andExpect(status().isCreated());
        String id = professionalId(account);

        Message message = null;
        for (long deadline = System.currentTimeMillis() + 15_000; System.currentTimeMillis() < deadline; ) {
            Message received = rabbit.receive(queue.getName(), 1_000);
            if (received != null && new String(received.getBody(), StandardCharsets.UTF_8).contains(id)) {
                message = received;
                break;
            }
        }

        assertThat(message)
                .as("evento publicado na fila ligada à exchange fithub.account")
                .isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(REGISTERED);
        assertThat(message.getMessageProperties().getReceivedExchange()).isEqualTo("fithub.account");
        assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(EventContracts.validate(
                        EventContract.PROFESSIONAL_REGISTERED_V1,
                        new String(message.getBody(), StandardCharsets.UTF_8)))
                .isEmpty();
        amqp.deleteQueue(queue.getName());
    }

    // Code Review do PR #199 (LOW): o CHECK do banco também recusa PERSONAL sem categoria (V3).
    @Test
    void bancoRecusaPersonalSemCategoriaMesmoPorEscritaDireta() {
        String account = newAccount();
        UUID organization = UUID.randomUUID();
        jdbc.sql("insert into organization (id, owner_account_id, created_at) values (:id, :owner, now())")
                .param("id", organization)
                .param("owner", account)
                .update();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("""
                                insert into professional (id, account_id, name, type, council, registration_number,
                                    registration_category, registration_state, situation, organization_id, role_status,
                                    created_at)
                                values (:id, :account, 'Ana', 'PERSONAL', 'CREF', '123', null, 'SP', 'DECLARED',
                                    :organization, 'PENDING', now())
                                """)
                        .param("id", UUID.randomUUID())
                        .param("account", account)
                        .param("organization", organization)
                        .update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
