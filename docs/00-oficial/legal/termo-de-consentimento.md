# Termo de consentimento do vínculo

**Versão:** v1
**Vigência:** desde a DEV-0036 (23/11/2026)
**Status:** rascunho para revisão jurídica

Este é o texto que o cliente lê no app antes de aceitar o convite de um profissional. O aceite registra a data, o
escopo e esta versão (NFR-01). O texto é o mesmo de `CURRENT_TEXT` em
[`ConsentTerms.java`](../../../services/client-service/src/main/java/com/fithub/client/domain/link/ConsentTerms.java),
no client-service. Mudar um exige mudar o outro, com versão nova.

> Ao aceitar, você autoriza este profissional a ver, enquanto o vínculo estiver ativo: seu perfil (nome, data de
> nascimento, gênero, altura e objetivo), seu perfil de saúde, suas avaliações corporais e suas fotos de evolução.
> Você pode encerrar o vínculo a qualquer momento; o profissional perde o acesso na hora. Você continua dono dos
> seus dados.

## Escopo registrado

- Perfil: nome, data de nascimento, gênero, altura e objetivo.
- Perfil de saúde: condições informadas e observações.
- Avaliações corporais: medidas, dobras e % de gordura.
- Fotos de evolução.

## Além do escopo do termo

- **Personal:**
  - vê os treinos registrados nas fichas dele e os recordes do cliente;
  - registra e vê as restrições de mobilidade do cliente (região do corpo e descrição).
- **Nutricionista:**
  - vê as refeições registradas nos planos dela, com os desvios e as fotos do prato;
  - registra e vê as restrições alimentares do paciente (alergias, intolerâncias e preferências).
- O cliente vê as próprias restrições, e elas só aparecem para o profissional com o vínculo ativo.

O personal nunca vê dados de nutrição, nem as restrições alimentares, e a nutricionista não vê as restrições de
mobilidade (BR-03).

## Revogação

O cliente revoga o consentimento ao encerrar o vínculo, a qualquer momento, no app. O profissional perde o acesso na
hora (BR-05). O histórico continua com o cliente (BR-06).
