# Imagem do client-service (DEV-0017). Contexto de build: a raiz do repositório, porque o serviço
# depende do POM pai e das libs (java-commons):
#   docker build -f services/client-service/Dockerfile -t fithub-client-service .
# O CI publica no GHCR com a tag do commit a cada merge na master que altera o serviço.

# Build: compila só este serviço e o que ele usa. Os testes rodam no job Java Services do CI.
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /ws
COPY pom.xml ./
COPY libs/ libs/
COPY services/ services/
RUN mvn -B -ntp -q -pl services/client-service -am package -DskipTests

# Runtime: só o JRE (mesma versão do JDK do build) e o jar, com usuário sem privilégio.
FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN groupadd --system --gid 10001 fithub \
    && useradd --system --uid 10001 --gid fithub --no-create-home --shell /usr/sbin/nologin fithub
WORKDIR /app
# O jar fica do root e só leitura para o processo: o serviço não escreve no próprio disco.
COPY --from=build /ws/services/client-service/target/client-service-*.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
