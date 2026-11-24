package com.fithub.training;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** training-service: Exercícios, fichas, execuções e recordes. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TrainingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainingServiceApplication.class, args);
    }

    /** Relógio das datas de gravação; os testes podem trocar. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
