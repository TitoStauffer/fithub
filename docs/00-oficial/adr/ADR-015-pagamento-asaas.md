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
2. **Ligação com o profissional:** o checkout fica gravado com o profissional. No primeiro pagamento de uma assinatura
   desconhecida, o account-service consulta a assinatura no Asaas e liga pelo `checkoutSession` dela. A documentação
   não garante que o `externalReference` do checkout chegue às cobranças, e ele não é usado para ligar.
3. **Webhook (`POST /webhooks/asaas`):**
   - fica fora do JWT e só aceita o token configurado no header `asaas-access-token`, comparado em tempo constante;
   - sem token, ou com token de menos de 32 caracteres, recusa tudo;
   - é idempotente pelo id da cobrança: o Asaas entrega pelo menos uma vez, e o `PAYMENT_CONFIRMED` e o
     `PAYMENT_RECEIVED` da mesma cobrança contam uma vez só.
4. **Período pago:** cada cobrança confirmada estende o `paid_until` até o vencimento seguinte, com 1 dia de folga
   para a próxima cobrança confirmar. Um pagamento mais antigo que chega depois nunca encurta o período. O
   `AssinaturaAlterada` já leva o novo `validUntil` aos serviços de prescrição.
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
- A ligação pelo `checkoutSession` precisa ser conferida no sandbox antes do primeiro profissional pagar. Essa
  conferência é um critério da DEV-0100.
- O cartão renova sozinho. Uma cobrança recusada não estende o período, e a assinatura vence no dia seguinte ao
  vencimento.
