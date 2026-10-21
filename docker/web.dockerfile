# compila o frontend Angular
FROM node:22-alpine AS build
WORKDIR /app

# instala dependências em camada separada para aproveitar o cache
COPY whisp-web/package.json whisp-web/package-lock.json ./
RUN npm ci

COPY whisp-web/ ./
RUN npx ng build --configuration production

# nginx sem privilégios (porta 8080): serve o SPA e faz proxy para os serviços
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/whisp-web/browser /usr/share/nginx/html
