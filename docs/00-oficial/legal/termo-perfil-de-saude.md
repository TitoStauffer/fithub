# Termo do perfil de saúde

**Versão:** v1
**Vigência:** desde a DEV-0179 (27/11/2026)
**Status:** rascunho para revisão jurídica

Este é o texto que o cliente lê no app na primeira vez que preenche o próprio perfil de saúde (condições e
observações). É a base legal desse dado sensível: o consentimento específico do cliente (LGPD art. 11, I). O aceite
registra a versão e o momento, sem IP nem aparelho, na tabela `health_consent` do client-service. O texto é o mesmo de
`CURRENT_TEXT` em
[`HealthConsentTerms.java`](../../../services/client-service/src/main/java/com/fithub/client/domain/profile/HealthConsentTerms.java).
Mudar um exige mudar o outro, com versão nova.

> Ao salvar, você autoriza o FitHub a guardar as condições de saúde e as observações que você informar, para mostrar
> a você e aos profissionais com quem você tiver vínculo ativo. Esses dados são seus: você pode mudá-los, exportá-los ou
> excluí-los a qualquer momento, junto com a sua conta.

## Como o aceite funciona

- Enquanto a conta não aceitou a versão vigente, o app mostra o texto e uma caixa desmarcada, e só salva com ela
  marcada. Depois do aceite, o texto não volta a aparecer, até uma versão nova.
- O aceite aparece na exportação dos dados (`healthConsents`) e é apagado junto com a conta (ADR-012).
- Os profissionais veem o perfil de saúde pelo termo de cada vínculo ([termo de consentimento](termo-de-consentimento.md)).
