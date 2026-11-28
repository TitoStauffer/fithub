# Imagem do account-service (DEV-0017). Contexto de build: a raiz do repositório, porque o serviço
# depende do POM pai e das libs (java-commons):
#   docker build -f services/account-service/Dockerfile -t fithub-account-service .
# O CI publica no GHCR com a tag do commit a cada merge na master (todos os serviços, DEV-0017).

# Repositório Maven opcional (DEV-0155): o CI passa o que o job já baixou, com --build-context m2=<repositório>.
# Com ele, o Maven roda offline (-o, DEV-0186) e nunca volta ao Maven Central: um artefato que falta vira erro, não
# download. Sem ele, vale este estágio vazio, e o Maven baixa tudo, como no build local.
FROM scratch AS m2

# Build: compila só este serviço e o que ele usa. Os testes rodam no job Java Services do CI.
FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /ws
COPY pom.xml ./
COPY libs/ libs/
COPY services/ services/
RUN --mount=type=bind,from=m2,target=/root/.m2/repository,rw \
    offline=""; if [ -n "$(ls -A /root/.m2/repository)" ]; then offline="-o"; fi; \
    mvn -B -ntp -q $offline -pl services/account-service -am package -DskipTests

# Runtime: só o JRE e o jar, com usuário sem privilégio. As duas imagens base ficam fixadas por digest
# (JDK 25.0.4.1 no build e no runtime); atualizar uma exige trocar o digest aqui nos quatro serviços.
FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f
RUN groupadd --system --gid 10001 fithub \
    && useradd --system --uid 10001 --gid fithub --no-create-home --shell /usr/sbin/nologin fithub
WORKDIR /app
# O jar fica do root e só leitura para o processo: o serviço não escreve no próprio disco.
COPY --from=build /ws/services/account-service/target/account-service-*.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
