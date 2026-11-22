package com.fithub.commons.messaging;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox transacional e consumo idempotente (DEV-0014, ADR-003). Com um {@link DataSource}, aplica as
 * tabelas técnicas e oferece {@link ProcessedEvents}. Com {@code fithub.messaging.exchange}, oferece
 * {@link OutboxWriter}, declara a exchange {@code topic} do serviço e, havendo RabbitMQ, liga o
 * {@link OutboxPublisher}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
})
@ConditionalOnBean({ DataSource.class, PlatformTransactionManager.class })
@EnableConfigurationProperties(FithubMessagingProperties.class)
public class FithubMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FithubCommonsMigrations fithubCommonsMigrations(DataSource dataSource) {
        FithubCommonsMigrations migrations = new FithubCommonsMigrations(dataSource);
        migrations.migrate();
        return migrations;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessedEvents processedEvents(DataSource dataSource, PlatformTransactionManager transactionManager,
            FithubCommonsMigrations migrations) {
        return new ProcessedEvents(JdbcClient.create(dataSource), new TransactionTemplate(transactionManager));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty("fithub.messaging.exchange")
    static class OutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxWriter outboxWriter(DataSource dataSource, JsonMapper jsonMapper, FithubMessagingProperties properties,
                FithubCommonsMigrations migrations) {
            return new OutboxWriter(JdbcClient.create(dataSource), jsonMapper, properties.exchange(), Clock.systemUTC());
        }

        @Bean
        @ConditionalOnBean(RabbitTemplate.class)
        public TopicExchange fithubServiceExchange(FithubMessagingProperties properties) {
            return new TopicExchange(properties.exchange(), true, false);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(RabbitTemplate.class)
        @ConditionalOnProperty(name = "fithub.messaging.publisher.enabled", matchIfMissing = true)
        public OutboxPublisher outboxPublisher(DataSource dataSource, PlatformTransactionManager transactionManager,
                RabbitTemplate rabbitTemplate, FithubMessagingProperties properties, FithubCommonsMigrations migrations) {
            return new OutboxPublisher(JdbcClient.create(dataSource), new TransactionTemplate(transactionManager), rabbitTemplate,
                    properties.publisher());
        }
    }
}
