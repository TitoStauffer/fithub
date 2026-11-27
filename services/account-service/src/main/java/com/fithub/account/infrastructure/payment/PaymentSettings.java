package com.fithub.account.infrastructure.payment;

import com.fithub.account.domain.payment.Pricing;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do pagamento (DEV-0100). O preço (DEV-0098) fica em {@code fithub.subscription.price.*}, para mudar
 * sem mudar código; o acesso ao Asaas, em {@code fithub.payment.asaas.*}, com a chave e o token do webhook só do
 * ambiente.
 */
@Configuration(proxyBeanMethods = false)
class PaymentSettings {

    /**
     * @param monthly      preço cheio do mês
     * @param launch       preço de lançamento
     * @param launchMonths meses pagos com o preço de lançamento
     * @param launchUntil  último dia (São Paulo) em que quem assina entra no preço de lançamento
     */
    @ConfigurationProperties("fithub.subscription.price")
    record Price(BigDecimal monthly, BigDecimal launch, Integer launchMonths, LocalDate launchUntil) {}

    /**
     * @param baseUrl      API do Asaas: {@code https://api-sandbox.asaas.com/v3} ou {@code https://api.asaas.com/v3}
     * @param apiKey       chave da API (header {@code access_token})
     * @param webhookToken token que o Asaas manda no header {@code asaas-access-token} do webhook
     * @param successUrl   para onde o Asaas leva o profissional depois de pagar (a tela de assinatura da web)
     * @param cancelUrl    para onde ele volta se desistir
     */
    @ConfigurationProperties("fithub.payment.asaas")
    record Asaas(String baseUrl, String apiKey, String webhookToken, String successUrl, String cancelUrl) {}

    @Bean
    Pricing pricing(Price price) {
        return new Pricing(price.monthly(), price.launch(), price.launchMonths(), price.launchUntil());
    }
}
