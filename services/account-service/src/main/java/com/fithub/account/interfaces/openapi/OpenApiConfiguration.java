package com.fithub.account.interfaces.openapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadados do OpenAPI gerado pelo springdoc em {@code /v3/api-docs}, que alimenta os tipos do front
 * (ENGENHARIA.md §5). Toda operação exige o JWT do Keycloak, como na java-commons.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String BEARER = "bearer";

    @Bean
    OpenAPI openApi(@Value("${spring.application.name}") String serviceName) {
        return new OpenAPI()
                .info(new Info().title(serviceName).version("v1").description("Profissional, organização e assinatura."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
