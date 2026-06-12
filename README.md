# Whisp

Aplicação de chat em tempo real construída com arquitetura de microsserviços. O backend usa Spring Boot (Java 21) dividido em serviços independentes que se comunicam via Kafka, e o frontend é uma SPA em Angular 21. Mensagens trafegam por WebSocket (STOMP) e são persistidas de forma assíncrona através de eventos.

## Visão geral da arquitetura

```
                    ┌─────────────────┐
                    │   whisp-web      │  Angular 21 (SPA)
                    │   (browser)      │
                    └───┬────────┬─────┘
              REST/HTTP │        │ WebSocket (STOMP /ws)
                        │        │
          ┌─────────────▼──┐  ┌──▼──────────────┐
          │  auth-service  │  │   chat-service   │
          │     :8081      │  │      :8082       │
          │  login/refresh │  │  rooms + STOMP   │
          └───────┬────────┘  └───┬──────────┬───┘
                  │               │ publica  │
                  │               │ evento   │
          ┌───────▼───────────────▼──────┐   │
          │         PostgreSQL            │   │  Kafka topic
          │  whisp_auth / whisp_chat /    │   │  chat.messages
          │       whisp_message           │   │
          └───────────────▲──────────────┘   │
                          │ persiste     ┌────▼─────────────┐
                          └──────────────│ message-service  │
                                         │      :8083       │
                                         │ consumer + REST  │
                                         │ histórico        │
                                         └──────────────────┘
              Redis :6379  ←  refresh tokens (auth-service)
```

Fluxo de uma mensagem:

1. O cliente envia a mensagem via STOMP para `chat-service` (`/app/chat/{roomId}`).
2. `chat-service` faz fan-out imediato para os assinantes do tópico `/topic/chat/{roomId}` (entrega em tempo real) e publica um `MessageEvent` no tópico Kafka `chat.messages`.
3. `message-service` consome o evento e persiste a mensagem em `whisp_message`.
4. O histórico é exposto via REST por `message-service` (`GET /rooms/{roomId}/messages`).

Em caso de falha no consumo, `message-service` aplica retry com backoff exponencial (1s, 2s, 4s) e, esgotadas as tentativas, envia o evento para a fila de dead-letter `chat.messages.dlq`.

## Serviços

| Módulo | Porta | Responsabilidade | Armazenamento |
|--------|-------|------------------|---------------|
| `auth-service` | 8081 | Registro, login, emissão e rotação de JWT, logout | PostgreSQL (`whisp_auth`), Redis (refresh tokens) |
| `chat-service` | 8082 | Gestão de salas (rooms) e mensageria em tempo real via WebSocket/STOMP | PostgreSQL (`whisp_chat`), Kafka (producer) |
| `message-service` | 8083 | Consome eventos do Kafka, persiste mensagens e serve o histórico | PostgreSQL (`whisp_message`), Kafka (consumer + DLQ) |
| `whisp-common` | - | Biblioteca compartilhada: eventos (`MessageEvent`, `DlqEvent`) e utilitários de JWT (`TokenIssuer`, `TokenVerifier`) | - |
| `whisp-web` | 4200 | Frontend SPA (Angular) | - |

## Stack

**Backend**
- Java 21, Spring Boot 3.5
- Spring Web, Spring Security, Spring Data JPA
- Spring WebSocket (STOMP sobre SockJS)
- Spring Kafka
- JJWT 0.12 para tokens
- Maven multi-módulo

**Infraestrutura**
- PostgreSQL 16
- Redis 7 (store de refresh tokens)
- Apache Kafka 7.6 (modo KRaft, sem ZooKeeper)
- Docker / Docker Compose

**Frontend**
- Angular 21 (standalone components, lazy loading)
- RxJS, `@stomp/stompjs`, `jwt-decode`
- Tailwind CSS
- Vitest

## Segurança e autenticação

- O `auth-service` emite um **access token** (JWT, validade de 15 min) retornado no corpo da resposta e mantido em memória pelo frontend.
- O **refresh token** (validade de 7 dias) é entregue em um cookie `httpOnly` com `SameSite=Strict`, restrito ao path `/auth/refresh`. Ele nunca é exposto ao JavaScript.
- Refresh tokens são armazenados no Redis, permitindo revogação no logout.
- `chat-service` e `message-service` validam o JWT de forma stateless usando o segredo compartilhado (`JWT_SECRET`), tanto em requisições REST quanto no handshake do WebSocket.

> O `JWT_SECRET` deve ser o mesmo entre todos os serviços, caso contrário os tokens emitidos pelo `auth-service` serão rejeitados pelos demais.

## Pré-requisitos

- Docker e Docker Compose
- Java 21 e Maven 3.9+ (apenas para rodar os serviços fora de containers)
- Node.js 20+ e npm 10+ (para o frontend)

## Configuração

Crie um arquivo `.env` na raiz do projeto com o segredo JWT (use uma chave base64):

```env
JWT_SECRET=<sua_chave_secreta_base64>
```

Para gerar uma chave:

```bash
openssl rand -base64 32
```

Cada serviço possui um `application-example.yaml` em `src/main/resources`. Ao rodar os serviços localmente (fora do Docker), copie o exemplo para `application.yaml` e ajuste credenciais de banco, `jwt.secret`, host do Kafka, etc.

## Executando com Docker Compose

A forma mais simples de subir todo o ambiente:

```bash
docker compose up --build
```

Isso provisiona PostgreSQL, Redis, Kafka e os três serviços de backend. O script `docker/init.sql` cria os bancos `whisp_chat` e `whisp_message` (o `whisp_auth` é criado pela própria imagem do Postgres).

Serviços expostos:

- auth-service: http://localhost:8081
- chat-service: http://localhost:8082
- message-service: http://localhost:8083
- PostgreSQL: `localhost:5432` (db `whisp_auth`, usuário `whisp`, senha `whisp123`)
- Redis: `localhost:6379`
- Kafka: `localhost:9092`

Healthchecks estão configurados em `/actuator/health` para cada serviço.

## Executando localmente (sem Docker)

1. Suba apenas a infraestrutura:

   ```bash
   docker compose up postgres redis kafka
   ```

2. Configure os `application.yaml` de cada serviço (ver seção de Configuração).

3. Compile e rode pela raiz do projeto:

   ```bash
   mvn clean install
   mvn -pl auth-service spring-boot:run
   mvn -pl chat-service spring-boot:run
   mvn -pl message-service spring-boot:run
   ```

## Frontend

```bash
cd whisp-web
npm install
npm start
```

A aplicação fica disponível em http://localhost:4200. Rotas: `/login`, `/register` e `/chat` (protegida por guard de autenticação).

## API

### auth-service (`/auth`)

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/auth/register` | Cria um novo usuário |
| POST | `/auth/login` | Autentica e retorna access token; define cookie `refresh_token` |
| POST | `/auth/refresh` | Gera novo access token a partir do cookie de refresh |
| POST | `/auth/logout` | Revoga o refresh token e limpa o cookie |

### chat-service

REST (`/rooms`, autenticado):

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/rooms` | Cria uma sala |
| GET | `/rooms` | Lista as salas do usuário autenticado |
| GET | `/rooms/all` | Lista todas as salas |
| POST | `/rooms/{roomId}/members` | Entra em uma sala |

WebSocket (STOMP):

- Endpoint de conexão: `/ws` (com SockJS)
- Enviar mensagem: destino `/app/chat/{roomId}`
- Receber mensagens: assinar `/topic/chat/{roomId}`

### message-service (`/rooms`)

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/rooms/{roomId}/messages?page=0&size=50` | Histórico paginado de mensagens da sala |

## Testes

Backend (por módulo ou agregado):

```bash
mvn test
```

Frontend:

```bash
cd whisp-web
npm test
```

## Estrutura do projeto

```
whisp/
├── auth-service/      Serviço de autenticação (Spring Boot)
├── chat-service/      Salas + WebSocket/STOMP (Spring Boot)
├── message-service/   Consumer Kafka + histórico (Spring Boot)
├── whisp-common/      Eventos e utilitários de JWT compartilhados
├── whisp-web/         Frontend Angular
├── docker/            Dockerfiles e init.sql
├── docker-compose.yml Orquestração do ambiente completo
└── pom.xml            POM pai (multi-módulo)
```
