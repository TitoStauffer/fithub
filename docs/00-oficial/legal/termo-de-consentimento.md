# Termo de consentimento do vínculo

**Versão:** v2
**Vigência:** desde a DEV-0160 (27/11/2026). A v1 valeu desde a DEV-0036 (23/11/2026).
**Status:** rascunho para revisão jurídica

Este é o texto que o cliente lê no app antes de aceitar o convite de um profissional. O aceite registra a data, o
escopo e esta versão (NFR-01). O texto é o mesmo de `CURRENT_TEXT` em
[`ConsentTerms.java`](../../../services/client-service/src/main/java/com/fithub/client/domain/link/ConsentTerms.java),
no client-service. Mudar um exige mudar o outro, com versão nova.

> Ao aceitar, você autoriza este profissional a ver, enquanto o vínculo estiver ativo: seu perfil (nome, data de
> nascimento, gênero, altura e objetivo), seu perfil de saúde, suas avaliações corporais e suas fotos de evolução.
> Você também autoriza o profissional a registrar e consultar restrições sobre você: de mobilidade, no caso do
> personal, e alimentares, no caso do nutricionista. Você pode encerrar o vínculo a qualquer momento; o profissional
> perde o acesso na hora. Você continua dono dos seus dados.

## Escopo registrado

- Perfil: nome, data de nascimento, gênero, altura e objetivo.
- Perfil de saúde: condições informadas e observações.
- Avaliações corporais: medidas, dobras e % de gordura.
- Fotos de evolução.
- **Restrições (desde a v2):**
  - o personal registra e vê as restrições de mobilidade do cliente (região do corpo e descrição);
  - a nutricionista registra e vê as restrições alimentares do paciente (alergias, intolerâncias e preferências);
  - o cliente vê as próprias restrições, e elas só aparecem para o profissional com o vínculo ativo.

O personal nunca vê dados de nutrição, nem as restrições alimentares, e a nutricionista não vê as restrições de
mobilidade (BR-03).

## Além do escopo do termo

- **Personal:** vê os treinos registrados nas fichas dele e os recordes do cliente.
- **Nutricionista:** vê as refeições registradas nos planos dela, com os desvios e as fotos do prato.

## Mudança de versão

Um vínculo aceito numa versão anterior aparece no app com o consentimento desatualizado. O cliente aceita a versão
vigente no próprio vínculo, sem refazê-lo, e o novo aceite fica registrado com a data e a versão. O aceite anterior
continua no histórico (DEV-0160).

## Revogação

O cliente revoga o consentimento ao encerrar o vínculo, a qualquer momento, no app. O profissional perde o acesso na
hora (BR-05). O histórico continua com o cliente (BR-06).
