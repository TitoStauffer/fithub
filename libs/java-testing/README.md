# java-testing

Suporte técnico de teste dos serviços (DEV-0015). Entra **só com escopo `test`**; nada daqui vai para
produção.

```xml
<dependency>
  <groupId>com.fithub</groupId>
  <artifactId>java-testing</artifactId>
  <scope>test</scope>
</dependency>
```

## O que entrega

| Peça | Uso |
|---|---|
| `HexagonalArchitecture` | Regras ArchUnit de [ENGENHARIA.md §5](../../docs/00-oficial/ENGENHARIA.md): domínio sem Spring, JPA ou java-commons; caso de uso sem JPA, RabbitMQ ou HTTP; dependências apontando para o domínio; toda classe numa das quatro camadas (menos a `@SpringBootApplication`). Camada ainda vazia não falha. Aplicação: `@ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);` |
| `ServiceIntegrationTest` | Base de `@SpringBootTest` com MockMvc, Postgres 17.12 e RabbitMQ 4.3.8 (Testcontainers, `@ServiceConnection`). Contêineres únicos por JVM: as classes de teste do serviço compartilham o mesmo contexto do Spring |
| `ServiceSkeletonContract` | O que todo serviço entrega: health público e `UP` (com banco e broker), readiness, OpenAPI com o título do serviço e o esquema `bearer`, 401 em Problem Details fora dos caminhos públicos e as migrations do serviço e da java-commons aplicadas. Também confere que o OpenAPI bate com o contrato versionado em `packages/api-types/openapi` (`OpenApiContract`, DEV-0022; `-Dfithub.openapi.update=true` regrava). Cada serviço tem uma subclasse vazia |

As regras têm teste próprio (`HexagonalArchitectureTest`): um serviço de exemplo correto passa, e um
serviço de exemplo para cada violação (Spring ou JPA no domínio, JPA no caso de uso, controller
chamando a infraestrutura, domínio chamando o caso de uso, classe fora das camadas) é recusado.

Os pacotes das camadas são ancorados em `com.fithub.<servico>`: um pacote de terceiros com o mesmo
nome nunca conta como camada.
