package com.fithub.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// scanBasePackages garante que os módulos de domínio (com.fithub.identity, etc.),
// que não estão no mesmo pacote do boot, sejam detectados pelo Spring.
@SpringBootApplication(scanBasePackages = "com.fithub")
@EnableJpaRepositories(basePackages = "com.fithub")
@EntityScan(basePackages = "com.fithub")
public class FitHubApplication {
    public static void main(String[] args){
        SpringApplication.run(FitHubApplication.class,args);
    }
}
