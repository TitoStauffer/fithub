# Política de privacidade do FitHub

**Versão:** v1
**Status:** rascunho para revisão jurídica (DEV-0103)

Esta política explica como o FitHub trata dados pessoais, de acordo com a Lei Geral de Proteção de Dados (Lei nº
13.709/2018, LGPD).

## 1. Controlador e encarregado

- **Controlador:** **[razão social a definir]**, CNPJ **[a definir]**.
- **Encarregado (DPO):** **[nome e e-mail a definir]**.

## 2. Que dados tratamos

| Quem | Dados | Para quê |
|---|---|---|
| Todos | Nome e e-mail da conta | Login, recuperação de senha e avisos da conta |
| Convidado | E-mail informado pelo profissional no convite, mesmo sem conta | Enviar o convite (vale 7 dias) |
| Cliente | Data de nascimento, gênero, altura e objetivo | Perfil e cálculos (idade, % de gordura) |
| Cliente | Perfil de saúde: condições e observações (dado sensível) | Orientar o profissional vinculado com segurança |
| Cliente | Avaliações corporais e fotos de evolução (dado sensível) | Acompanhar a evolução |
| Cliente | Restrições de mobilidade (região do corpo e descrição), registradas pelo personal (dado sensível) | Alertar o personal na ficha sobre exercícios que envolvem a região |
| Cliente | Restrições alimentares (alergias, intolerâncias e preferências), registradas pela nutricionista (dado sensível) | Alertar a nutricionista no plano sobre alimentos com o termo |
| Cliente | Treinos, recordes (calculados e lançados), refeições, desvios e fotos do prato | Acompanhar a aderência e a evolução |
| Profissional | Tipo e número do registro no conselho (declarado) | Identificar o profissional |
| Profissional | Assinatura: plano e datas | Liberar o lado profissional |

Não coletamos IP nem identificador do aparelho no consentimento.

## 3. Bases legais

| Tratamento | Base legal |
|---|---|
| Conta e funcionamento do app | Execução do contrato (art. 7º, V) |
| Dados de saúde, avaliações, fotos e restrições compartilhados com o profissional, ou registrados por ele | Consentimento específico e destacado do cliente, no [termo de consentimento](termo-de-consentimento.md) de cada vínculo (art. 11, I). As restrições de mobilidade e alimentares estão no termo desde a versão v2 |
| Assinatura e cobrança do profissional | Execução do contrato e cumprimento de obrigação legal |
| Registros de acesso de profissionais a dados sensíveis | Legítimo interesse e dever de segurança (art. 7º, IX, e art. 46; NFR-03) |

## 4. Com quem compartilhamos

- **Profissional vinculado:**
  - só com o vínculo ativo e só o que o termo descreve;
  - o personal nunca vê dados de nutrição;
  - encerrado o vínculo, o acesso acaba na hora.
- **Operadores que nos ajudam a manter o serviço:**
  - hospedagem: **[provedor a definir]**;
  - envio de e-mail: **[a definir]**;
  - meio de pagamento: **[a definir na DEV-0098]**, só para profissionais.
- **Nunca** vendemos dados nem os usamos para publicidade.

## 5. Como protegemos

- Fotos e dados de saúde ficam com acesso restrito. As fotos são servidas só por link temporário de 5 minutos e
  nunca são públicas.
- A comunicação é cifrada (HTTPS).
- No celular, o app guarda a sessão só no cofre do sistema. A foto do prato escolhida da galeria continua na
  galeria, como antes.
- As leituras do perfil de saúde pelo profissional são registradas, sem o conteúdo lido. O registro das leituras
  de avaliações e fotos vem com a DEV-0106.

## 6. Por quanto tempo guardamos

- **Enquanto a conta existir.**
- **Quando você exclui a conta,** apagamos de vez, em minutos, tudo o que é seu: perfil, saúde, avaliações, fotos,
  treinos e refeições registrados, recordes, restrições, ficha própria, vínculos, consentimentos e o login. Não há
  prazo para desistir nem como recuperar a conta.
- **As fichas e os planos que o profissional criou** continuam com ele, sem nada que identifique você.
- **Cópias de segurança** guardam o dado apagado por no máximo 30 dias, até expirarem. Nesse prazo guardamos só o
  identificador da conta excluída, para refazer a exclusão se uma cópia for restaurada.
- **Registros técnicos** (logs) não levam dado pessoal e ficam 30 dias.
- **Registros de acesso de profissionais** aos seus dados de saúde ficam 6 meses, mesmo depois da exclusão, pela
  mesma base da seção 3 (legítimo interesse e dever de segurança), para apurar um acesso indevido. Eles guardam só
  quem acessou, o quê e quando, nunca o conteúdo.
- Os detalhes estão no [ADR-012](../adr/ADR-012-retencao-e-exclusao.md).

## 7. Seus direitos

Você pode, a qualquer momento:

- **saber e acessar** quais dados temos;
- **corrigir** dados incompletos ou errados, no próprio app;
- **exportar** seus dados em formato legível (DEV-0104);
- **excluir** sua conta e seus dados (DEV-0105);
- **revogar o consentimento** encerrando o vínculo com o profissional;
- **reclamar** à Autoridade Nacional de Proteção de Dados (ANPD).

Pedidos que o app ainda não atende sozinho: **[e-mail do encarregado a definir]**.

## 8. Menores de idade

O FitHub exige 18 anos ou mais (QUESTÃO-05).

## 9. Mudanças nesta política

Mudanças relevantes geram uma versão nova, avisada no app antes de valer.
