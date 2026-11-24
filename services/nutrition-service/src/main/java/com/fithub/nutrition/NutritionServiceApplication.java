package com.fithub.nutrition;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** nutrition-service: Alimentos, planos e registro de refeições. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NutritionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutritionServiceApplication.class, args);
    }

    /** Relógio das datas de gravação; os testes podem trocar. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
