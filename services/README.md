# services

Um microsserviço por pasta, cada um com o próprio banco PostgreSQL e comunicação por eventos (RabbitMQ). Ver [ENGENHARIA.md §2](../docs/00-oficial/ENGENHARIA.md).

| Serviço | Dono de | Porta local | Exchange |
|---|---|---|---|
| `account-service` | Profissional, organização e assinatura | 8081 | `fithub.account` |
| `client-service` | Perfil, saúde, vínculos, consentimento, avaliações e fotos | 8082 | `fithub.client` |
| `training-service` | Exercícios, fichas, execuções e recordes | 8083 | `fithub.training` |
| `nutrition-service` | Alimentos, planos e registro de refeições | 8084 | `fithub.nutrition` |

## Esqueleto (DEV-0015)

O template entrou com o `account-service` (DEV-0015) e foi aplicado a `client`, `training` e `nutrition` na DEV-0141.

Todo serviço tem a mesma forma ([ENGENHARIA.md §5](../docs/00-oficial/ENGENHARIA.md)):

```
src/main/java/com/fithub/<servico>/
├── <Servico>ServiceApplication.java   única classe fora das camadas
├── domain/          sem Spring, sem JPA, sem java-commons
├── application/     casos de uso; sem JPA, RabbitMQ nem HTTP
├── infrastructure/  adaptadores de saída (JPA, RabbitMQ, S3)
└── interfaces/      adaptadores de entrada (REST, OpenAPI)
src/main/resources/
├── application.yml
└── db/migration/    migrations do serviço (Flyway); V1__inicial.sql abre o histórico
```

- **java-commons** traz segurança (JWT do Keycloak), Problem Details, logging JSON, outbox e consumo
  idempotente. Só `/actuator/health/**` e `/v3/api-docs/**` são públicos.
- **Actuator:** só o endpoint `health` é exposto, com probes `liveness` e `readiness`.
- **OpenAPI** (springdoc, sem Swagger UI) em `/v3/api-docs`, com o esquema `bearer` exigido em toda
  operação. É a fonte dos tipos do front.
- **JPA** com `ddl-auto: validate`: esquema só por migration.

## Testes

Cada serviço tem dois testes, com a base de [libs/java-testing](../libs/java-testing/README.md):

- `ArchitectureTest`: as regras ArchUnit da arquitetura hexagonal.
- `<Servico>ServiceSkeletonTest`: o serviço sobe inteiro contra Postgres e RabbitMQ reais
  (Testcontainers) e confere health, OpenAPI, segurança e migrations.

Na raiz: `mvn -B verify` (exige JDK 25 e Docker; sem JDK 25 local, veja o comando em contêiner no
[README da java-commons](../libs/java-commons/README.md#build)).

## Imagem Docker (DEV-0017)

Cada serviço tem um `Dockerfile` multi-stage: o build Maven compila só o serviço e o que ele usa, e o
runtime tem só o JRE e o jar, rodando como o usuário `fithub` (uid 10001). O contexto de build é a
raiz do repositório (o serviço depende do POM pai e das libs):

```sh
docker build -f services/account-service/Dockerfile -t fithub-account-service .
```

No PR, o CI constrói a imagem dos serviços alterados e recusa imagem que rode como root
([check-image-user.mjs](../tools/repo-checks/check-image-user.mjs)). No merge na master, publica no
GHCR como `ghcr.io/<dono>/fithub-<servico>:<sha do commit>`. Mudança no POM pai, nas libs ou no
`.dockerignore` gera a imagem de todos os serviços.

## Rodar localmente

Com o compose de desenvolvimento no ar ([infra/README.md](../infra/README.md)):

```sh
mvn -B install -DskipTests          # uma vez, instala java-commons e java-testing
mvn -pl services/account-service spring-boot:run
```

Os padrões do `application.yml` apontam para o compose. Fora dele, configure por variável de ambiente:
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `KEYCLOAK_ISSUER_URI` e `SERVER_PORT`. O realm do Keycloak
chega na DEV-0024; até lá, toda rota protegida responde 401.
