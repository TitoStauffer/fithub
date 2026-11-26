package com.fithub.commons.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Medidores agregados das métricas de sucesso (DEV-0113): um {@link AggregateGauges} por serviço. */
@AutoConfiguration(
        afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnBean(MeterRegistry.class)
public class FithubMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AggregateGauges aggregateGauges(MeterRegistry registry, Environment environment) {
        Duration interval =
                environment.getProperty("fithub.metrics.refresh-interval", Duration.class, Duration.ofMinutes(5));
        return new AggregateGauges(registry, interval);
    }
}
