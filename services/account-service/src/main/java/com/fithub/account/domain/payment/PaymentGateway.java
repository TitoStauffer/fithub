package com.fithub.account.domain.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * O meio de pagamento (porta do domínio, DEV-0100): o Asaas, pelo checkout hospedado dele, só com cartão de crédito em
 * cobrança recorrente mensal (DEV-0098). O pagador informa os dados na página do Asaas; nada deles passa por aqui.
 */
public interface PaymentGateway {

    /** Cria o checkout da assinatura mensal com a primeira cobrança em {@code firstDueDate}. */
    Checkout createCheckout(UUID professionalId, BigDecimal value, LocalDate firstDueDate);

    /** O checkout que gerou a assinatura do meio de pagamento, quando ela veio de um. */
    Optional<String> checkoutOf(String subscriptionId);

    /** Troca o valor das próximas cobranças da assinatura, inclusive as já geradas e ainda pendentes. */
    void changeValue(String subscriptionId, BigDecimal value);

    /** O checkout criado: o id e o link da página de pagamento. */
    record Checkout(String id, String link) {}
}
