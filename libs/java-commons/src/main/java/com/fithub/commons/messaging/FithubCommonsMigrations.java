package com.fithub.commons.messaging;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * Aplica as tabelas técnicas da java-commons ({@code db/fithub-commons}) com um Flyway próprio e
 * histórico separado ({@value #HISTORY_TABLE}), para que a numeração não colida com as migrations do
 * serviço. Num banco que já tem tabelas do serviço, a linha de base fica na versão 0, então a V1 da
 * biblioteca ainda roda.
 */
public final class FithubCommonsMigrations {

    public static final String LOCATION = "classpath:db/fithub-commons";
    public static final String HISTORY_TABLE = "flyway_fithub_commons_history";

    private final DataSource dataSource;
    private final String schema;

    public FithubCommonsMigrations(DataSource dataSource) {
        this(dataSource, null);
    }

    public FithubCommonsMigrations(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    public int migrate() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (schema != null) configuration.schemas(schema).defaultSchema(schema);
        return configuration.load().migrate().migrationsExecuted;
    }
}
