package com.fithub.client;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** client-service: Perfil, saúde, vínculos, consentimento, avaliações e fotos. */
@SpringBootApplication
public class ClientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientServiceApplication.class, args);
    }

    /** Relógio do serviço; os testes trocam por um fixo. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
