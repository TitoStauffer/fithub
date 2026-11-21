# Backend

Spring Boot + Java 21 + Maven. Monólito modular (DDD): `shared-kernel` → `platform` → módulos de domínio (`modules/identity`, ...) → `boot`.

## Rodando localmente

1. Suba o Postgres:
   ```
   docker compose -f infrastructure/docker/docker-compose.yml up -d
   ```
2. (Opcional) copie `.env.example` para `.env` na raiz do repo e ajuste se necessário — os valores padrão já batem com o `docker-compose.yml`.
3. Rode a aplicação a partir da raiz do `backend/`:
   ```
   mvn -pl boot -am spring-boot:run
   ```
4. Verifique o health-check:
   ```
   curl http://localhost:8080/actuator/health
   ```
   Deve retornar `{"status":"UP", ...}` com o componente `db` também `UP`.

## Módulos

- `shared-kernel`: tipos base de domínio (Aggregate, Value Object, Domain Event), sem dependências de outros módulos.
- `platform`: infraestrutura técnica compartilhada (JPA, Flyway, config).
- `modules/identity`: primeiro domínio de negócio (cadastro/autenticação de usuários).
- `boot`: ponto de entrada, agrega todos os módulos e sobe o contexto Spring Boot.

Migrations Flyway ficam em `platform/src/main/resources/db/migration`.
