# Architecture Decision Records

Decisões técnicas relevantes do FitHub. Uma decisão nova vira um ADR numerado (AGENTS.md); um ADR
superado ganha status "substituído por ADR-NNN" em vez de ser apagado.

| ADR | Decisão | Status |
|---|---|---|
| [ADR-001](ADR-001-microsservicos.md) | Microsserviços independentes desde o MVP | aceito |
| [ADR-002](ADR-002-java-25-spring-boot-4.md) | Java 25 e Spring Boot 4 | aceito |
| [ADR-003](ADR-003-rabbitmq-outbox.md) | RabbitMQ com outbox transacional | aceito |
| [ADR-004](ADR-004-keycloak.md) | Keycloak como provedor de identidade | aceito |
| [ADR-005](ADR-005-deploy-compose-vps.md) | Deploy inicial com Docker Compose em VPS | aceito |
| [ADR-006](ADR-006-seaweedfs-s3-local.md) | SeaweedFS como S3 compatível no ambiente local | aceito |
| [ADR-007](ADR-007-papeis-e-clients-do-realm.md) | Papéis e clients do realm `fithub` | aceito |
| [ADR-008](ADR-008-atribuicao-de-papel-pelo-account-service.md) | Atribuição do papel de profissional pelo account-service | aceito |
| [ADR-009](ADR-009-identidade-comum-entre-servicos.md) | O `sub` do Keycloak é o id da pessoa em todos os serviços | aceito |

Formato: contexto, decisão, alternativas consideradas e consequências.
