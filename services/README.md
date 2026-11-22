# services

Um microsserviço por pasta, cada um com o próprio banco PostgreSQL e comunicação por eventos (RabbitMQ). Ver [ENGENHARIA.md §2](../docs/00-oficial/ENGENHARIA.md).

| Serviço | Dono de |
|---|---|
| `account-service` | Profissional, organização e assinatura |
| `client-service` | Perfil, saúde, vínculos, consentimento, avaliações e fotos |
| `training-service` | Exercícios, fichas, execuções e recordes |
| `nutrition-service` | Alimentos, planos e registro de refeições |
