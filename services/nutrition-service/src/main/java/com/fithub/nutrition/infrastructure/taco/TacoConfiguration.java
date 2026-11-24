package com.fithub.nutrition.infrastructure.taco;

import com.fithub.nutrition.application.FoodCatalogSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** A base externa de alimentos do serviço: a Tabela TACO do CSV versionado (DEV-0075). */
@Configuration
class TacoConfiguration {

    @Bean
    FoodCatalogSource foodCatalogSource(TacoSettings settings, ResourceLoader resources) {
        return new TacoCsvSource(resources.getResource(settings.resource()));
    }
}
