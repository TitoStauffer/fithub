package com.fithub.account;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** account-service: Profissional, organização e assinatura. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    /** Relógio do serviço; os testes trocam por um fixo. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
