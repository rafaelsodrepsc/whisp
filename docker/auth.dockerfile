# compila o projeto e gera o JAR
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# copia os poms antes do código para cachear dependências separadamente
COPY pom.xml .
COPY whisp-common/pom.xml whisp-common/
COPY auth-service/pom.xml auth-service/
COPY chat-service/pom.xml chat-service/
COPY message-service/pom.xml message-service/

# instala o pom pai e o whisp-common no repositório local
# -N significa non-recursive - instala só o módulo raiz sem os filhos
COPY whisp-common/src whisp-common/src
RUN mvn install -N -q && mvn install -pl whisp-common -q

# baixa dependências do serviço em camada isolada
RUN mvn dependency:go-offline -pl auth-service -q

# copia o código e compila
COPY auth-service/src auth-service/src
RUN mvn clean package spring-boot:repackage -pl auth-service -DskipTests -q

# imagem final só com o JAR
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl necessário para o healthcheck do Docker Compose
RUN apk add --no-cache curl

COPY --from=build /app/auth-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]