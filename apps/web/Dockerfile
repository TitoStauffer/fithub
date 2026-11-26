# Imagem da web do profissional (DEV-0176, ADR-014). Contexto de build: a raiz do repositório, porque o app usa o
# workspace pnpm (lockfile da raiz e packages/api-types):
#   docker build -f apps/web/Dockerfile -t fithub-web .
# O CI publica no GHCR com a tag do commit a cada merge na master, junto com as imagens dos serviços.

# Build: os estáticos do Vite, com as APIs e o Keycloak em caminhos do mesmo domínio. A mesma imagem serve qualquer
# domínio: o app resolve os caminhos na origem da página (src/lib/config.ts).
FROM node:24.22.3-alpine@sha256:d8d97e479cdc64f052f81f129362168717c59783ade8dcc1abd22e1cca8dbd7c AS build
WORKDIR /ws
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/ packages/
COPY apps/web/package.json apps/web/
RUN pnpm install --frozen-lockfile --filter web...
COPY apps/web/ apps/web/
ENV VITE_KEYCLOAK_URL=/auth \
    VITE_ACCOUNT_API_URL=/api/account \
    VITE_CLIENT_API_URL=/api/client \
    VITE_TRAINING_API_URL=/api/training \
    VITE_NUTRITION_API_URL=/api/nutrition
RUN pnpm --filter web run build

# Runtime: só os estáticos num nginx sem privilégio (porta 8080), sem Node. As duas imagens base ficam fixadas por
# digest; atualizar uma exige trocar o digest aqui.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:e2a8fca8aa356d66159aaf8a877a9c126773635adf210503eefaaeaffeed6ad7
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /ws/apps/web/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
