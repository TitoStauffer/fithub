package com.fithub.commons.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Erro do banco no handler genérico (DEV-0150): o Postgres põe a linha recusada ou a chave repetida na mensagem, e
 * isso nunca pode ir para o log. Vai só o tipo, o SQLState e a constraint.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class DatabaseErrorLogTest {

    private static final String MARKER = "MARCADOR-SAUDE-0150";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");

    private static JdbcClient jdbc;

    @BeforeAll
    static void createTable() {
        jdbc = JdbcClient.create(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.sql("create table measurement (id int primary key, note text not null,"
                        + " weight numeric constraint ck_measurement_weight check (weight > 0), tag text)")
                .update();
        jdbc.sql("insert into measurement (id, note, weight) values (1, ?, 80)")
                .param(MARKER)
                .update();
    }

    @Test
    void aRejectedRowNeverReachesTheLog(CapturedOutput output) {
        DataAccessException error = rejected("insert into measurement (id, note, weight) values (2, ?, -1)", MARKER);
        assertThat(error.getMessage())
                .as("pré-condição: o Postgres traz a linha")
                .contains(MARKER);

        var problem = new FithubProblemDetailsHandler().handleUnexpected(error);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(output)
                .contains("SQLState 23514")
                .contains("ck_measurement_weight")
                .doesNotContain(MARKER);
    }

    @Test
    void aRepeatedKeyNeverReachesTheLog(CapturedOutput output) {
        DataAccessException error =
                rejected("insert into measurement (id, note, weight) values (1, ?, 70)", "outra nota");
        // A chave repetida vem no Detail: "Key (id)=(1) already exists". O marcador aqui é a própria chave.
        assertThat(error.getMessage())
                .as("pré-condição: o Postgres traz a chave")
                .contains("(id)=(1)");

        new FithubProblemDetailsHandler().handleUnexpected(error);

        assertThat(output)
                .contains("SQLState 23505")
                .contains("measurement_pkey")
                .doesNotContain("(id)=(1)");
    }

    // No NOT NULL, a primeira linha não tem nome de constraint, e o Detail traz a linha: o nome nunca sai dele.
    @Test
    void aNotNullViolationDoesNotTakeAConstraintNameFromTheRow(CapturedOutput output) {
        DataAccessException error = rejected(
                "insert into measurement (id, note, weight, tag) values (3, null, 10, ?)",
                "constraint \"" + MARKER + "\"");
        assertThat(error.getMessage())
                .as("pré-condição: o Postgres traz a linha")
                .contains(MARKER);

        new FithubProblemDetailsHandler().handleUnexpected(error);

        assertThat(output).contains("SQLState 23502").doesNotContain(MARKER);
    }

    @Test
    void aDataAccessErrorWithoutSqlCauseKeepsOnlyTheType(CapturedOutput output) {
        new FithubProblemDetailsHandler().handleUnexpected(new DataRetrievalFailureException("linha com " + MARKER));

        assertThat(output).contains("DataRetrievalFailureException").doesNotContain(MARKER);
    }

    @Test
    void otherErrorsKeepTheirMessageInTheLog(CapturedOutput output) {
        new FithubProblemDetailsHandler().handleUnexpected(new IllegalStateException("estado inválido do pedido"));

        assertThat(output).contains("estado inválido do pedido");
    }

    private static DataAccessException rejected(String sql, String param) {
        return catchThrowableOfType(
                DataAccessException.class, () -> jdbc.sql(sql).param(param).update());
    }
}
