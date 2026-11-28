# ADR-015 — Pagamento da assinatura pelo checkout do Asaas

- **Status:** aceita. O meio e as formas de pagamento foram decididos pelo Tito; o desenho da integração é decisão do
  Agent, para revisão.
- **Data:** 26/11/2026
- **Tarefa:** DEV-0100 (#92), a partir da DEV-0098 (#90)

## Contexto

O profissional paga um plano mensal (PRD, seção 9.2):

- R$ 200/mês, com R$ 150/mês nos 3 primeiros meses pagos para quem assinar até a data de corte do lançamento;
- 14 dias grátis sem cartão.

O Tito escolheu o Asaas.

O checkout hospedado do Asaas só aceita **cartão** em cobrança recorrente: Pix e boleto não entram na recorrência por
ali. Pela API de assinaturas, Pix e boleto exigiriam guardar o CPF/CNPJ do pagador no FitHub e o pagamento manual a
cada mês. Também não existe desconto nativo para as N primeiras cobranças.

## Decisão

1. **Checkout hospedado, só cartão recorrente (decisão do Tito):**
   - o account-service cria o checkout (`POST /professionals/me/subscription/checkout`) sem os dados do pagador;
   - o pagador informa os dados, inclusive o CPF/CNPJ, na página do Asaas, e nada disso passa pelo FitHub;
   - Pix e boleto ficam para depois do MVP.
2. **Ligação com o profissional:** o checkout fica gravado com o profissional. Quando o Asaas avisa que a assinatura foi
   criada (ou, se esse aviso falhar, no primeiro pagamento), o account-service consulta a assinatura e liga pelo
   `checkoutSession` dela. O sandbox confirmou que o `externalReference` do checkout não chega à assinatura, por isso
   ele não é usado para ligar.
   - **Uma assinatura viva por profissional:** com uma assinatura ligada e não encerrada, o checkout novo dá 409, e o
     checkout aberto de até 23 h é reaproveitado. Sem isso, um segundo checkout pago no teste grátis geraria duas
     cobranças recorrentes no cartão (Code Review do PR #328).
3. **Webhook (`POST /webhooks/asaas`):**
   - fica fora do JWT e só aceita o token configurado no header `asaas-access-token`, comparado em tempo constante;
   - sem token, ou com token de menos de 32 caracteres, recusa tudo;
   - é idempotente pelo id da cobrança: o Asaas entrega pelo menos uma vez, e o `PAYMENT_CONFIRMED` e o
     `PAYMENT_RECEIVED` da mesma cobrança contam uma vez só.
4. **Período pago:** cada cobrança confirmada estende o `paid_until` até o vencimento seguinte, com 1 dia de folga
   para a próxima cobrança confirmar. Um pagamento mais antigo que chega depois nunca encurta o período. O
   `AssinaturaAlterada` já leva o novo `validUntil` aos serviços de prescrição.
   - **Estorno e chargeback (DEV-0182, decisão do Tito em 27/11/2026):** o `PAYMENT_REFUNDED`, o
     `PAYMENT_CHARGEBACK_REQUESTED` e o `PAYMENT_CHARGEBACK_DISPUTE` de uma cobrança gravada encerram o período pago
     no momento do evento, nunca depois do que já estava, e gravam o `AssinaturaAlterada`. O profissional volta ao
     estado vencido, a menos que o teste grátis ainda corra. O que já foi entregue continua com o cliente (BR-14).
   - O estorno não encerra a assinatura do Asaas. Enquanto ela estiver ativa lá, a próxima cobrança confirmada dela
     reabre o período, e o checkout novo continua recusado (409, uma assinatura viva por profissional). Um checkout novo
     só abre depois do `SUBSCRIPTION_INACTIVATED` ou do `SUBSCRIPTION_DELETED` (Code Review do PR #360).
   - A cobrança estornada fica no histórico, com a data do estorno. Conta uma vez só: o estorno repetido, ou o
     chargeback que chega depois, não encerra um período pago por uma cobrança nova. A confirmação da mesma cobrança
     entregue de novo também não reabre o período. O estorno de uma cobrança que não foi gravada é ignorado com aviso.
   - **Limite:** o estorno que chegasse antes da confirmação da mesma cobrança seria ignorado, e a confirmação depois
     estenderia o período. A fila sequencial do webhook no Asaas (Consequências) entrega os eventos de uma cobrança
     na ordem, então isso não acontece com a configuração prevista. Se a fila deixar de ser sequencial, o estorno de
     cobrança desconhecida precisa ser guardado (Code Review do PR #360).
5. **Preço de lançamento:**
   - a assinatura nasce com R$ 150 quando o checkout é criado até a data de corte;
   - no 3º pagamento confirmado, o account-service troca o valor para R$ 200 no Asaas (`PUT /v3/subscriptions/{id}`,
     com `updatePendingPayments`, porque o Asaas gera a cobrança com antecedência);
   - preço, preço de lançamento, número de meses e data de corte ficam em configuração.
6. **Histórico das cobranças confirmadas:** alimenta as métricas de conversão do teste para pago e de retenção mensal
   (DEV-0113, PRD seção 19), só agregadas.

## Alternativas consideradas

- **API de assinaturas com Pix e boleto:** mais formas de pagamento, mas com o CPF guardado no FitHub (mais dado
  pessoal sob responsabilidade do produto) e pagamento manual a cada mês, que é onde a pessoa mais esquece.
- **Cupom de desconto por 3 meses no Asaas:** o desconto da assinatura não é documentado como limitado a N cobranças.
- **Idempotência pelo id do evento:** redundante com a da cobrança. Nenhum teste conseguiria provar a diferença, então
  ficou só a da cobrança.

## Consequências

- A conta de produção do Asaas pede CNPJ (MEI serve) e conta bancária; o sandbox é gratuito.
- O **Tito** cria a conta e cadastra o webhook no painel do Asaas (URL `https://<domínio>/api/account/webhooks/asaas`,
  fila sequencial e o token do `.env`).
- A ligação pelo `checkoutSession` foi conferida no sandbox: a assinatura criada pelo checkout traz o id dele.
- O cartão renova sozinho. Uma cobrança recusada não estende o período, e a assinatura vence no dia seguinte ao
  vencimento.
