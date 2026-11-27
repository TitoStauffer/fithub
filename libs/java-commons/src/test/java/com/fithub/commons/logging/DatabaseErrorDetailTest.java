package com.fithub.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fithub.commons.testapp.TestApplication;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Com o padrão da java-commons, o driver do Postgres não põe na mensagem da exceção o Detail com a linha recusada nem
 * a chave repetida (DEV-0183). Sem isso, a linha chegaria ao log pelo Hibernate, pelos consumidores ou por qualquer
 * handler que registre a exceção.
 */
@Testcontainers
@SpringBootTest(
        classes = TestApplication.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
class DatabaseErrorDetailTest {

    private static final String MARKER = "MARCADOR-SAUDE-0183";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");

    @Autowired
    private DataSource dataSource;

    @Test
    void aRejectedRowOrRepeatedKeyIsNotInAnyExceptionMessage() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("create table detail_check (id int primary key, note text, weight numeric"
                        + " constraint ck_detail_check_weight check (weight > 0))")
                .update();
        jdbc.sql("insert into detail_check values (1, ?, 10)").param(MARKER).update();

        Throwable check = catchThrowable(() -> jdbc.sql("insert into detail_check values (2, ?, -1)")
                .param(MARKER)
                .update());
        Throwable repeated = catchThrowable(() ->
                jdbc.sql("insert into detail_check values (1, 'outra', 10)").update());

        assertThat(messages(check))
                .as("CHECK")
                .noneMatch(message -> message.contains(MARKER))
                .anyMatch(message -> message.contains("ck_detail_check_weight"));
        assertThat(messages(repeated))
                .as("chave repetida")
                .noneMatch(message -> message.contains("(id)=(1)"))
                .anyMatch(message -> message.contains("detail_check_pkey"));
    }

    // O Hibernate 7 grava em batch por padrão (o dialeto do Postgres usa 15). No batch, o driver monta a mensagem com o
    // SQL da entrada recusada: os parâmetros também não podem aparecer nela.
    @Test
    void aRejectedBatchEntryIsNotInAnyExceptionMessage() throws Exception {
        JdbcClient.create(dataSource)
                .sql("create table detail_batch (id int primary key, note text, weight numeric"
                        + " constraint ck_detail_batch_weight check (weight > 0))")
                .update();
        Throwable batch;
        try (var connection = dataSource.getConnection();
                var insert = connection.prepareStatement("insert into detail_batch values (?, ?, ?)")) {
            for (int id = 1; id <= 3; id++) {
                insert.setInt(1, id);
                insert.setString(2, MARKER);
                insert.setInt(3, id == 2 ? -1 : 10);
                insert.addBatch();
            }
            batch = catchThrowable(insert::executeBatch);
        }

        List<String> messages = new ArrayList<>(messages(batch));
        if (batch instanceof java.sql.SQLException sql && sql.getNextException() != null) {
            messages.addAll(messages(sql.getNextException()));
        }
        assertThat(messages).as("batch").noneMatch(message -> message.contains(MARKER));
    }

    /** As mensagens da exceção e de todas as causas, como um log com a pilha inteira as mostraria. */
    private static List<String> messages(Throwable error) {
        assertThat(error).isNotNull();
        List<String> messages = new ArrayList<>();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            messages.add(String.valueOf(cause.getMessage()));
        }
        return messages;
    }
}
