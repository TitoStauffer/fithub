# PRD — FitHub

**Versão:** 1.0.0
**Data:** 2026-11-21
**Dono:** Tito Stauffer
**Status:** Em revisão

> Este é o documento oficial de produto do FitHub. Onde ele conflitar com
> documentos da primeira versão (hoje só no histórico do git, ver
> [docs/HISTORICO.md](../HISTORICO.md)), **vale este**. Decisões técnicas ficam em
> [ENGENHARIA.md](ENGENHARIA.md); a divisão do trabalho fica em
> [BACKLOG.md](BACKLOG.md).

Legenda: **FATO** = decidido · **PREMISSA** = assumido, ainda não validado ·
**QUESTÃO** = em aberto (ver seção 21).

---

## 1. Visão geral do produto

**Nome:** FitHub

**Descrição:** plataforma SaaS que conecta personal trainers e nutricionistas
aos seus clientes. O profissional prescreve treinos ou dietas e acompanha a
evolução; o cliente recebe as prescrições, registra o que fez e acompanha seu
próprio progresso.

**Resumo:** o FitHub tem três lados independentes:

| Lado | Quem usa | Onde | O que faz |
|---|---|---|---|
| **Personal** | Personal trainer (academia no futuro) | Web | Monta fichas de treino, avalia o aluno, acompanha execução e evolução |
| **Nutricionista** | Nutricionista | Web | Monta planos alimentares, avalia o paciente, acompanha aderência e evolução |
| **Cliente** | Aluno / paciente | App mobile | Recebe treino e dieta, registra execução, peso, medidas, fotos e recordes |

O cliente pode contratar **só personal, só nutricionista, os dois ou nenhum**
(nesse caso usa o app sozinho para registrar seus treinos e sua evolução).

**Visão de produto:** ser a ferramenta de trabalho do profissional autônomo de
fitness e nutrição no Brasil, com o cliente como dono dos próprios dados e do
próprio histórico.

**Modelo de negócio (FATO):** o profissional paga uma assinatura mensal para
gerenciar seus clientes. O cliente usa o app gratuitamente. A plataforma não
intermedeia pagamento entre cliente e profissional.

---

## 2. Problema

**Problema:** personal trainers e nutricionistas autônomos acompanham seus
clientes com ferramentas genéricas e espalhadas (planilha, PDF, WhatsApp,
papel). O cliente recebe a ficha ou a dieta, mas não tem onde registrar o que
fez de forma que o profissional enxergue.

**Situação atual (PREMISSA — validar com profissionais):**
- A ficha de treino e o plano alimentar são entregues em PDF ou imagem.
- O acompanhamento depende do cliente relatar por mensagem.
- Avaliações corporais ficam em planilhas ou papel, sem histórico fácil de
  comparar.
- Personal e nutricionista do mesmo cliente não compartilham informações
  básicas (peso, medidas, condições de saúde), e o cliente repete os dados para
  cada um.
- Quando o cliente troca de profissional, perde o histórico.

**Impacto:**
- O profissional gasta tempo com tarefa manual e tem pouca visibilidade da
  aderência, o que dificulta ajustar a prescrição.
- O cliente perde motivação por não enxergar a própria evolução.
- O histórico do cliente fica preso ao profissional ou se perde.

---

## 3. Contexto

- Existe uma primeira versão do backend (módulos identity, patients, training e
  nutrition) e do frontend web, construída a partir da especificação anterior.
  Ela serve de referência, mas a arquitetura muda para microsserviços (ver
  [ENGENHARIA.md](ENGENHARIA.md)).
- No Brasil, a prescrição de dieta é atividade privativa do nutricionista (CRN)
  e a prescrição de treino é do profissional de educação física (CREF).
- Dados de saúde, medidas e fotos de corpo são **dados pessoais sensíveis**
  pela LGPD e exigem consentimento específico e controle de acesso.
- Há consultoria presencial e online. No online o profissional não consegue
  aferir dobras cutâneas, então a avaliação precisa funcionar com medidas
  feitas pelo próprio cliente.

---

## 4. Objetivos

**GOAL-01** — O profissional consegue montar e entregar uma ficha de treino ou
um plano alimentar completo pela plataforma, sem depender de PDF ou planilha.

**GOAL-02** — O profissional enxerga o que o cliente realmente executou
(treino por série, refeições cumpridas) e a evolução corporal ao longo do tempo.

**GOAL-03** — O cliente tem, num só app, o treino e a dieta do dia e o registro
do que fez, com a própria evolução visível.

**GOAL-04** — O cliente informa seus dados uma única vez e controla com qual
profissional compartilha.

**GOAL-05** — O cliente mantém todo o seu histórico ao trocar ou encerrar o
vínculo com um profissional.

**GOAL-06** — Validar que profissionais autônomos pagam assinatura pela
ferramenta.

---

## 5. Fora dos objetivos (non-goals)

- **NON-GOAL-01** — Intermediar pagamento entre cliente e profissional.
- **NON-GOAL-02** — Atender academias com vários personais e gestor (o modelo
  de dados deve permitir, mas não entra no MVP).
- **NON-GOAL-03** — Recomendações ou geração automática de treino ou dieta por IA.
- **NON-GOAL-04** — Integração com wearables e balanças inteligentes.
- **NON-GOAL-05** — Notificações push (lembretes e alertas ficam para depois).
- **NON-GOAL-06** — Chat entre profissional e cliente.
- **NON-GOAL-07** — Modo offline no app.
- **NON-GOAL-08** — Marketplace de profissionais ou de fichas prontas.
- **NON-GOAL-09** — Outros idiomas além de pt-BR.
- **NON-GOAL-10** — Dieta para cliente sem nutricionista.

---

## 6. Usuários

### Personal trainer (autônomo)
- **Contexto:** atende alunos presencialmente ou online, monta fichas e
  reavalia periodicamente.
- **Necessidade:** montar fichas rápido a partir de uma base de exercícios,
  entregar ao aluno com vídeo de execução, ver se o aluno treinou e quanto
  evoluiu em carga e medidas.
- **Valor:** menos trabalho manual, mais visibilidade, aparência profissional.

### Nutricionista
- **Contexto:** atende pacientes presencialmente ou online, monta planos
  alimentares e reavalia composição corporal.
- **Necessidade:** montar planos com alimentos de uma base confiável e macros
  calculados, oferecer substituições, ver se o paciente cumpre o plano.
- **Valor:** menos cálculo manual, acompanhamento real da aderência.

### Cliente (aluno / paciente)
- **Contexto:** treina, faz dieta ou os dois, com ou sem profissional.
- **Necessidade:** saber o que fazer hoje, registrar fácil, ver a evolução.
- **Valor:** clareza, motivação e posse do próprio histórico.

### Administrador da plataforma
- **Contexto:** Tito / operação.
- **Necessidade:** gerenciar contas, assinaturas e a base de exercícios e
  alimentos.

---

## 7. Stakeholders

| Stakeholder | Interesse | Impacto |
|---|---|---|
| Fundador (produto e engenharia) | Validar o modelo de assinatura com o menor escopo possível | Define prioridades e constrói |
| Profissionais (pagantes) | Ferramenta que poupe tempo e passe credibilidade | Determinam receita e adoção |
| Clientes | App simples e gratuito | Determinam engajamento, que é o valor que o profissional percebe |
| Conselhos (CREF/CRN) | Prescrição feita por profissional habilitado | Restrição regulatória |
| Titulares de dados (LGPD) | Controle e segurança dos dados sensíveis | Restrição legal |

---

## 8. Jornadas

### 8.1 Profissional começa a usar
```
Profissional se cadastra (web)
   ↓
Informa dados profissionais (tipo: personal ou nutri, nº CREF/CRN)
   ↓
Assina um plano (ou entra em período de teste — QUESTÃO-01)
   ↓
Convida um cliente (e-mail, link ou código)
   ↓
Cliente aceita e autoriza o compartilhamento dos dados
   ↓
Profissional vê a ficha do cliente e faz a primeira avaliação
   ↓
Profissional monta a ficha de treino / o plano alimentar e publica
```

### 8.2 Cliente usa no dia a dia
```
Cliente abre o app
   ↓
Vê o próximo treino da sequência (ex.: "Treino B") e as refeições do dia
   ↓
Executa o treino registrando cada série (carga e reps pré-preenchidas)
   ↓
Marca cada refeição como feita / parcial / não feita e a alternativa usada
   ↓
Vê evolução: cargas, recordes, peso, medidas, aderência
```

### 8.3 Cliente sem profissional
```
Cliente se cadastra no app
   ↓
Preenche perfil e perfil de saúde
   ↓
Monta a própria ficha e registra treinos, peso, medidas, fotos e recordes
   ↓
(Opcional) Aceita convite de um profissional no futuro
```

### 8.4 Fim do vínculo
```
Cliente ou profissional encerra o vínculo
   ↓
Profissional perde acesso aos dados novos do cliente
   ↓
Profissional mantém apenas o que ele prescreveu (registro do trabalho)
   ↓
Cliente mantém todo o histórico
```

---

## 9. Requisitos funcionais

### 9.1 Conta e acesso
- **FR-01** — Cadastro e login por e-mail e senha, e login com Google.
- **FR-02** — Recuperação de senha por e-mail.
- **FR-03** — O usuário tem um tipo de conta: profissional (personal ou
  nutricionista) ou cliente. Uma mesma pessoa pode ter as duas, com a mesma
  conta (QUESTÃO-02, decidida).
- **FR-04** — O profissional informa seu número de registro (CREF ou CRN) no
  cadastro. No MVP o registro é só declarado: o formato é validado e a
  existência no conselho não (QUESTÃO-08, decidida). Formato aceito: número
  de 1 a 6 dígitos e UF de emissão; no CREF, também a categoria (G ou P).

### 9.2 Assinatura do profissional
- **FR-05** — O profissional assina um plano mensal para usar o lado
  profissional da plataforma.
- **FR-06** — Sem assinatura ativa, o profissional não consegue criar novas
  prescrições, mas o cliente continua vendo o que já recebeu.
- Detalhes de planos, preço, teste grátis e meio de pagamento: QUESTÃO-01.

### 9.3 Perfil do cliente (dono: cliente)
- **FR-07** — O cliente mantém seu perfil: nome, data de nascimento, gênero,
  objetivo (ex.: emagrecimento, hipertrofia, condicionamento, saúde).
- **FR-08** — O cliente mantém seu perfil de saúde: condições gerais
  (diabetes, hipertensão, gravidez, cirurgias recentes, outras) com campo
  livre.
- **FR-09** — A idade é calculada a partir da data de nascimento.

### 9.4 Vínculo e consentimento
- **FR-10** — O profissional convida um cliente por e-mail, link ou código.
  O cliente também pode se vincular informando o código do profissional.
- **FR-11** — Ao aceitar, o cliente vê exatamente quais dados serão
  compartilhados e dá consentimento explícito.
- **FR-12** — O cliente vê seus vínculos ativos e pode encerrar qualquer um a
  qualquer momento. O profissional também pode encerrar.
- **FR-13** — O cliente tem no máximo 1 personal e 1 nutricionista ativos ao
  mesmo tempo.

### 9.5 Avaliação corporal (dono: cliente; registrada por cliente ou profissional)
- **FR-14** — Registro de peso e circunferências (pescoço, tórax, cintura,
  abdômen, quadril, braço, antebraço, coxa, panturrilha) por data.
- **FR-15** — Cada avaliação tem um tipo: **presencial** (feita pelo
  profissional) ou **online/autoaferida** (feita pelo cliente).
- **FR-16** — Na avaliação presencial, o profissional pode registrar dobras
  cutâneas, e o sistema calcula o % de gordura pelos protocolos Pollock de 3 e
  de 7 dobras.
- **FR-17** — Na avaliação online, o sistema estima o % de gordura pela
  fórmula da Marinha dos EUA (circunferências + altura) e o app guia o cliente
  em como medir cada circunferência.
- **FR-18** — % de gordura de bioimpedância pode ser informado manualmente,
  marcado como tal.
- **FR-19** — Os gráficos de evolução indicam a origem de cada ponto
  (presencial, online, bioimpedância), porque os métodos não são diretamente
  comparáveis.
- **FR-20** — Fotos de evolução com poses padronizadas (frente, lado, costas)
  por data, com comparativo lado a lado entre duas datas.
- **FR-21** — Altura registrada no perfil e atualizável.

### 9.6 Lado personal — exercícios
- **FR-22** — Base de exercícios com nome, grupos musculares, equipamento,
  instruções e vídeo ou animação de execução.
- **FR-23** — Busca e filtro por nome, grupo muscular e equipamento.
- **FR-24** — O personal cadastra exercícios próprios com link de vídeo,
  visíveis só para ele.

### 9.7 Lado personal — ficha do aluno e restrições
- **FR-25** — O personal vê o perfil, o perfil de saúde e o histórico de
  avaliações do aluno vinculado.
- **FR-26** — O personal registra restrições de mobilidade do aluno (região do
  corpo + descrição), visíveis só no lado personal e para o próprio aluno.
- **FR-27** — Ao incluir na ficha um exercício que envolve uma região
  restrita, o sistema **alerta** o personal, que pode manter o exercício
  (PREMISSA: alerta e não bloqueio — o personal é o responsável técnico).

### 9.8 Lado personal — ficha de treino
- **FR-28** — O personal monta uma ficha com N divisões nomeadas em sequência
  (A, AB, ABC, ABCD, ABCDE…).
- **FR-29** — As divisões giram em ordem (A→B→C→A…), sem dia da semana fixo.
  O app mostra ao aluno qual é o próximo treino.
- **FR-30** — Cada exercício na divisão tem: séries, repetições (número ou
  faixa, ex.: 8–12), carga sugerida, descanso, observação e técnica opcional
  (bi-set, tri-set, drop-set, rest-pause).
- **FR-31** — Cada exercício pode ter exercícios alternativos (ex.: máquina
  ocupada).
- **FR-32** — A ficha tem data de início e validade em semanas. O personal vê
  quais fichas estão vencendo.
- **FR-33** — Editar uma ficha publicada gera uma nova versão. O histórico de
  versões e os registros feitos em cada versão são preservados.
- **FR-34** — O aluno tem uma ficha ativa por vez vinda do personal. Uma ficha
  própria (feita pelo cliente) pode coexistir (QUESTÃO-03).

### 9.9 Lado nutricionista — alimentos
- **FR-35** — Base de alimentos da Tabela TACO com macros (proteína,
  carboidrato, gordura, calorias) por 100 g.
- **FR-36** — A nutricionista cadastra alimentos e receitas próprias, visíveis
  só para ela.
- **FR-37** — Quantidades em gramas ou ml como base, exibindo também a medida
  caseira (colher, unidade, fatia…) quando houver conversão cadastrada.
- **FR-38** — Alimentos podem ter a etiqueta "vegano", com filtro na busca.

### 9.10 Lado nutricionista — ficha do paciente e restrições
- **FR-39** — A nutricionista vê o perfil, o perfil de saúde e o histórico de
  avaliações do paciente vinculado.
- **FR-40** — A nutricionista registra restrições alimentares (alergias,
  intolerâncias) e preferências (ex.: vegano, não gosta de peixe), visíveis só
  no lado nutri e para o próprio paciente.
- **FR-41** — Ao incluir um alimento conflitante com uma alergia ou
  intolerância cadastrada, o sistema alerta a nutricionista
  (PREMISSA: alerta e não bloqueio).

### 9.11 Lado nutricionista — plano alimentar
- **FR-42** — A nutricionista define as refeições do plano com nome, horário
  sugerido e ordem (ex.: café da manhã, pré-treino, almoço).
- **FR-43** — Cada refeição tem uma lista de alimentos com quantidade.
  O sistema calcula os macros da refeição e do dia.
- **FR-44** — Cada alimento pode ter até 2 alternativas (secundária e
  terciária), escolhidas livremente. O sistema mostra a diferença de macros em
  relação ao alimento principal.
- **FR-45** — Observações por refeição e pelo plano.
- **FR-46** — Editar um plano publicado gera uma nova versão, preservando o
  histórico.

### 9.12 Lado cliente — execução
- **FR-47** — O cliente vê a ficha ativa, o próximo treino da sequência e o
  vídeo de cada exercício.
- **FR-48** — O cliente registra o treino **por série**: carga e repetições
  reais, pré-preenchidas com o prescrito ou com o último treino.
- **FR-49** — O cliente pode trocar um exercício por uma alternativa prevista
  e pode registrar um comentário ou sensação no treino.
- **FR-50** — O cliente vê as refeições do dia e marca cada uma como
  **feita, parcial ou não feita**, indicando qual alternativa usou.
- **FR-51** — O cliente pode registrar um desvio (texto livre) e anexar foto
  opcional do prato.
- **FR-52** — Sem profissional, o cliente pode montar a própria ficha
  (mesma estrutura de divisões) e registrar treinos.

### 9.13 Recordes pessoais
- **FR-53** — O sistema detecta recordes automaticamente a partir das séries
  registradas: maior carga e maior 1RM estimado por exercício.
- **FR-54** — O cliente pode lançar recordes manuais, marcados como tal.
- **FR-55** — O personal vinculado vê os recordes do aluno.

### 9.14 Evolução e acompanhamento
- **FR-56** — Para o personal: aderência (treinos feitos vs. esperados no
  período), evolução de carga e volume por exercício, recordes, evolução
  corporal.
- **FR-57** — Para a nutricionista: aderência (refeições feitas vs.
  prescritas), quais alternativas o paciente mais usa, desvios, evolução
  corporal.
- **FR-58** — Para o cliente: seu histórico de treinos, cargas, recordes,
  aderência à dieta, peso, medidas, % de gordura e fotos.
- **FR-59** — O profissional tem uma lista de clientes com sinalização de quem
  está sem registrar atividade há X dias (substitui as notificações push no
  MVP).

---

## 10. Regras de negócio

- **BR-01** — O perfil, o perfil de saúde, as avaliações corporais, as fotos e
  os registros de execução pertencem ao cliente.
- **BR-02** — Um profissional só acessa dados de clientes com vínculo ativo e
  consentimento dado.
- **BR-03** — O personal **não** vê dados de nutrição (plano, refeições,
  restrições alimentares). A nutricionista **não** vê dados de treino (fichas,
  execuções, restrições de mobilidade, recordes).
- **BR-04** — Ambos os profissionais vinculados veem o perfil, o perfil de
  saúde, as avaliações corporais e as fotos (dados compartilhados por
  consentimento).
- **BR-05** — Ao encerrar o vínculo, o profissional perde acesso imediato a
  qualquer dado novo do cliente e aos dados compartilhados. Ele mantém apenas
  as prescrições que ele mesmo criou.
- **BR-06** — O cliente mantém todo o histórico após o fim de qualquer vínculo,
  incluindo as prescrições recebidas (somente leitura).
- **BR-07** — Máximo de 1 personal e 1 nutricionista ativos por cliente.
  Para trocar, o vínculo atual precisa ser encerrado.
- **BR-08** — Registros de execução (séries, refeições) podem ser corrigidos
  pelo cliente no mesmo dia (QUESTÃO-04); depois disso ficam imutáveis.
- **BR-09** — Uma prescrição publicada nunca é sobrescrita: editar gera nova
  versão; os registros apontam para a versão que estava ativa.
- **BR-10** — Um treino ou plano tem que ter pelo menos um exercício ou uma
  refeição com pelo menos um alimento para ser publicado.
- **BR-11** — Restrições de mobilidade e alimentares geram alerta, não
  bloqueio (PREMISSA).
- **BR-12** — O % de gordura sempre carrega o método de origem; métodos
  diferentes não são misturados numa mesma linha de gráfico sem indicação.
- **BR-13** — Exercícios e alimentos próprios de um profissional são visíveis
  apenas para ele e para os clientes que receberam prescrições com eles.
- **BR-14** — Profissional sem assinatura ativa não cria nem edita prescrições.
  O que já foi entregue continua visível para o cliente.
- **BR-15** — Cliente menor de idade: QUESTÃO-05.

---

## 11. Requisitos não funcionais

- **NFR-01 (Privacidade/LGPD)** — Consentimento registrado com data, escopo e
  versão do termo. O cliente consegue exportar e excluir seus dados.
- **NFR-02 (Segurança)** — Fotos e dados de saúde armazenados com acesso
  restrito, servidos apenas por link temporário e nunca públicos.
- **NFR-03 (Auditoria)** — Acessos de profissionais a dados sensíveis do
  cliente são registrados.
- **NFR-04 (Disponibilidade dos dados)** — Backups diários do banco, com
  restauração testada.
- **NFR-05 (Usabilidade)** — Registrar uma série no app deve exigir no máximo
  um toque quando o cliente repete o prescrito.
- **NFR-06 (Independência)** — Falha no lado de nutrição não pode impedir o
  uso do lado de treino, e vice-versa.
- **NFR-07 (Idioma)** — Interface em pt-BR, com textos preparados para
  internacionalização futura.

---

## 12. Fluxos principais

### Publicar ficha de treino
1. Personal abre o aluno vinculado.
2. Cria uma ficha, define as divisões (ex.: ABC) e a validade.
3. Adiciona exercícios a cada divisão a partir da base, com prescrição.
4. Sistema alerta se algum exercício envolve uma região restrita.
5. Personal publica; o aluno passa a ver a ficha no app.

### Registrar treino
1. Cliente abre o app e vê "Próximo: Treino B".
2. Para cada exercício, confirma ou ajusta carga e reps de cada série.
3. Finaliza o treino; a sequência avança para o Treino C.
4. Sistema atualiza recordes, se houver.

### Publicar plano alimentar
1. Nutricionista abre o paciente vinculado.
2. Cria as refeições e adiciona alimentos com quantidade.
3. Define alternativas por alimento; vê a diferença de macros.
4. Sistema alerta conflitos com alergias e intolerâncias.
5. Publica; o paciente vê as refeições do dia no app.

### Avaliação online
1. Cliente (ou profissional pedindo ao cliente) inicia uma avaliação online.
2. App guia a medição de cada circunferência e as fotos padronizadas.
3. Sistema calcula o % de gordura estimado e registra como "online".
4. Profissionais vinculados veem a nova avaliação.

---

## 13. Fluxos alternativos

- Convite enviado para e-mail sem conta → cliente cria a conta pelo link e o
  vínculo é aceito em seguida.
- Cliente já tem um personal ativo e recebe convite de outro → precisa
  encerrar o atual antes de aceitar.
- Cliente recusa o consentimento → vínculo não é criado.
- Ficha vencida → aluno continua vendo e registrando; personal vê o aviso de
  vencimento.
- Exercício indisponível → cliente usa a alternativa prevista.
- Cliente não faz a refeição → marca "não feita", opcionalmente com desvio.
- Assinatura do profissional vence → prescrições existentes seguem visíveis;
  criação bloqueada.

---

## 14. Casos de borda

| Cenário | Comportamento esperado |
|---|---|
| Ficha editada no meio da semana | Registros já feitos ficam ligados à versão anterior; próximos usam a nova |
| Cliente registra treino sem internet | Fora do MVP (sem offline); o app avisa que não há conexão e não perde o que já foi digitado na tela |
| Registro duplicado (toque duplo) | Não gera dois registros |
| Vínculo encerrado enquanto o profissional está com a ficha aberta | Próxima ação do profissional é negada |
| Exercício próprio do personal usado em ficha e depois o vínculo acaba | Cliente continua vendo o exercício nas fichas antigas |
| Medidas incompletas na avaliação online | Registra o que foi informado; % de gordura não é calculado sem as medidas necessárias |
| Peso registrado pelo cliente e pelo profissional no mesmo dia | Ambos ficam, com autor e tipo identificados |
| Cliente exclui a conta | Dados excluídos ou anonimizados conforme política de retenção (QUESTÃO-06) |
| Base de exercícios externa muda ou sai do ar | Exercícios já importados continuam funcionando (cópia local) |

---

## 15. Escopo

### No MVP
- Conta, login (e-mail e Google) e recuperação de senha
- Assinatura do profissional (forma mínima — QUESTÃO-01)
- Perfil e perfil de saúde do cliente
- Vínculo por convite com consentimento e encerramento
- Avaliação corporal presencial (dobras, Pollock) e online (Marinha dos EUA),
  peso, circunferências e fotos padronizadas
- Lado personal: base de exercícios, exercícios próprios, restrições de
  mobilidade, ficha em divisões com versões
- Lado nutri: base TACO, alimentos próprios, restrições e preferências, plano
  com alternativas e versões
- Lado cliente: ficha e dieta do dia, registro por série, registro de
  refeições, ficha própria, recordes
- Telas de evolução para os três lados e sinalização de clientes inativos
- Exportação e exclusão dos dados do cliente (LGPD)

### Fora do MVP
- Academias com vários personais
- Notificações push e lembretes
- Chat
- IA
- Wearables e balanças
- Offline
- Marketplace
- Dieta para cliente sem nutricionista
- Outros idiomas

---

## 16. Dependências

| Dependência | Impacto |
|---|---|
| Base externa de exercícios com vídeo/animação (ex.: ExerciseDB) | Sem ela, o personal começa com a base vazia. Licença e custo precisam ser validados (QUESTÃO-07) |
| Tabela TACO | Base de alimentos da nutri. Pública, precisa de importação e de conversões caseiras |
| Login com Google | Necessário para FR-01 |
| Meio de pagamento recorrente | Necessário para a assinatura (QUESTÃO-01) |
| Lojas de aplicativo (Apple/Google) | Publicação do app do cliente; contas de desenvolvedor e revisão das lojas |
| Envio de e-mail | Convites e recuperação de senha |

---

## 17. Premissas

- **PREMISSA-01** — Profissionais autônomos pagariam uma assinatura mensal por
  essa ferramenta.
- **PREMISSA-02** — O cliente aceita registrar o treino série a série se o
  pré-preenchimento deixar isso rápido.
- **PREMISSA-03** — Alertas (e não bloqueios) são suficientes para restrições,
  porque o profissional é o responsável técnico.
- **PREMISSA-04** — 1 personal + 1 nutri ativos por cliente cobre a grande
  maioria dos casos.
- **PREMISSA-05** — A fórmula da Marinha dos EUA é aceita pelas nutricionistas
  como estimativa no atendimento online.

---

## 18. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Profissional não vê valor suficiente para pagar | Modelo de receita falha | Validar com profissionais reais antes e durante o MVP; teste grátis |
| Cliente não registra (abandono do app) | Profissional não vê aderência e perde interesse | Registro com um toque, pré-preenchimento, sinalização de inatividade |
| Licença ou custo da base de exercícios | Base vazia ou custo inesperado | Validar licença antes de integrar; permitir exercícios próprios desde o início |
| Vazamento de dados sensíveis (fotos, saúde) | Dano legal e de reputação | Storage privado, links temporários, auditoria, consentimento granular |
| Escopo grande para um dev solo | MVP não sai | Entregar por épicos demonstráveis; cortar o que não valida a hipótese |
| Complexidade operacional dos microsserviços | Lentidão no desenvolvimento | Infraestrutura simples (ver ENGENHARIA.md), serviços pequenos, automação desde o início |
| Revisão das lojas de app | Atraso no lançamento | Iniciar publicação cedo (beta fechado) |

---

## 19. Métricas de sucesso

Metas numéricas **a definir** com base nos primeiros profissionais.

| Métrica | O que mede |
|---|---|
| Profissionais pagantes | Validação do modelo de receita (GOAL-06) |
| Conversão do teste grátis para pago | Valor percebido pelo profissional |
| Clientes ativos por profissional | Uso real da ferramenta |
| % de clientes vinculados que registram ao menos um treino ou refeição por semana | Engajamento do cliente (GOAL-02, GOAL-03) |
| Prescrições publicadas por profissional por mês | Uso do lado profissional (GOAL-01) |
| Retenção mensal de profissionais | Saúde do negócio |
| Clientes que mantêm uso após o fim de um vínculo | Valor do histórico próprio (GOAL-05) |

---

## 20. Critérios de aceitação (MVP)

```gherkin
Dado um personal com assinatura ativa e um aluno vinculado
Quando o personal publica uma ficha ABC
Então o aluno vê "Próximo: Treino A" no app

Dado um aluno que concluiu o Treino A
Quando ele abre o app novamente
Então o próximo treino exibido é o Treino B

Dado um aluno registrando um exercício prescrito com 3x10 a 40 kg
Quando ele confirma a série sem alterar
Então a série é registrada com 10 reps e 40 kg

Dado um aluno com restrição de joelho
Quando o personal adiciona um agachamento à ficha
Então o sistema exibe um alerta e permite manter o exercício

Dado um paciente com intolerância à lactose cadastrada
Quando a nutricionista adiciona leite integral a uma refeição
Então o sistema exibe um alerta

Dado um alimento principal com uma alternativa
Quando a nutricionista visualiza a alternativa
Então vê a diferença de proteína, carboidrato, gordura e calorias

Dado um cliente vinculado a um personal e a uma nutricionista
Quando o personal acessa os dados do cliente
Então não vê plano alimentar, refeições nem restrições alimentares

Dado um cliente que encerra o vínculo com o personal
Quando o personal tenta acessar as avaliações do cliente
Então o acesso é negado
E o personal ainda vê as fichas que ele mesmo criou

Dado um cliente com vínculo encerrado
Quando ele abre seu histórico
Então vê todos os treinos registrados e as fichas recebidas

Dado um cliente com personal ativo
Quando recebe convite de outro personal
Então não consegue aceitar sem encerrar o vínculo atual

Dado uma avaliação online com pescoço, cintura, quadril e altura informados
Quando a avaliação é salva
Então o % de gordura estimado é calculado e marcado como "online"

Dado um aluno que registrou 100 kg x 5 no supino, acima do recorde anterior
Quando o treino é finalizado
Então um novo recorde de supino é registrado
```

---

## 21. Questões em aberto

- **QUESTÃO-01** — Assinatura: preço, planos (limite de clientes por plano?),
  teste grátis, meio de pagamento (Stripe, Asaas, Mercado Pago…) e se o
  primeiro lançamento já cobra ou começa com cobrança manual/beta gratuito.
- ~~**QUESTÃO-02**~~ — **Decidida pelo Tito em 22/11/2026:** sim, a mesma
  conta pode ser profissional e cliente.
- **QUESTÃO-03** — Quando o cliente tem ficha própria e passa a ter personal, a
  ficha própria fica arquivada, coexiste ou é visível ao personal?
- **QUESTÃO-04** — Por quanto tempo o cliente pode corrigir um registro de
  execução?
- **QUESTÃO-05** — Clientes menores de idade: permitidos? Se sim, exigem
  consentimento do responsável (LGPD, art. 14). **Enquanto aberta:** o perfil
  do cliente exige 18 anos ou mais (decisão do Agent na ausência do Tito, para
  revisão; DEV-0032).
- **QUESTÃO-06** — Política de retenção após exclusão de conta, e o que
  acontece com as prescrições guardadas pelo profissional.
- ~~**QUESTÃO-07**~~ — **Decidida pelo Agent na ausência do Tito, para revisão
  (DEV-0052, [ADR-010](adr/ADR-010-base-de-exercicios.md)):** base inicial do
  wger (gratuita, Creative Commons por exercício, com atribuição), mídia só com
  licença explícita e vídeo principalmente pela URL do personal. Fontes pagas com
  vídeo ficam para decisão de custo do Tito.
- ~~**QUESTÃO-08**~~ — **Decidida pelo Tito em 22/11/2026:** só declarado
  no MVP, com o formato validado e uma situação (`DECLARED`) gravada para
  permitir verificação futura. O formato exato (número de 1 a 6 dígitos, UF e,
  no CREF, categoria G ou P) foi decisão do Agent na ausência do Tito, para
  revisão (DEV-0029).
- **QUESTÃO-09** — O cliente vê as restrições que o profissional registrou
  sobre ele? (premissa atual: sim)
- **QUESTÃO-10** — Qual o período padrão de "inatividade" para sinalizar um
  cliente ao profissional?
