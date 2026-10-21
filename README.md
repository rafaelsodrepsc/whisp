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
- O **refresh token** (validade de 7 dias) é entregue em um cookie `httpOnly` com `SameSite=Strict`, restrito ao path `/auth` (para que o logout também receba o cookie e possa revogar o token no Redis). Ele nunca é exposto ao JavaScript.
- Refresh tokens são armazenados no Redis, permitindo revogação no logout.
- `chat-service` e `message-service` validam o JWT de forma stateless usando o segredo compartilhado (`JWT_SECRET`), tanto em requisições REST quanto no handshake do WebSocket.

> O `JWT_SECRET` deve ser o mesmo entre todos os serviços, caso contrário os tokens emitidos pelo `auth-service` serão rejeitados pelos demais.

## Pré-requisitos

- Docker e Docker Compose
- Java 21 e Maven 3.9+ (apenas para rodar os serviços fora de containers)
- Node.js 20+ e npm 10+ (para o frontend)

## Configuração

Copie `.env.example` para `.env` e preencha (o `.env` nunca vai para o git):

| Variável | Para que serve |
|---|---|
| `JWT_SECRET` | segredo HS256 em base64, o mesmo para os três serviços (`openssl rand -base64 32`) |
| `POSTGRES_PASSWORD` | senha do PostgreSQL (não há valor padrão) |
| `REDIS_PASSWORD` | senha do Redis, onde ficam os refresh tokens (não há valor padrão) |
| `PUBLIC_ORIGIN` | origem pública do site, sem barra final (`https://chat.seudominio.com`). Usada em CORS e na origem permitida do WebSocket. Várias origens: separe por vírgula |
| `COOKIE_SECURE` | `true` (padrão) marca o cookie de refresh como `Secure`; use `false` só em HTTP local |
| `WEB_PORT` | porta do host para o nginx (padrão 80) |
| `JAVA_TOOL_OPTIONS` | opcional, heap das três JVMs (padrão `-Xmx384m`) |
| `KAFKA_HEAP_OPTS` | opcional, heap do Kafka (padrão `-Xmx512m -Xms256m`) |

Os serviços falham na subida se `JWT_SECRET` não for um base64 de pelo menos 32 bytes, e o compose recusa subir sem as senhas e o `PUBLIC_ORIGIN`.

Cada serviço possui um `application-example.yaml` em `src/main/resources`. Ao rodar os serviços localmente (fora do Docker), copie o exemplo para `application.yaml` e ajuste credenciais de banco, `jwt.secret`, host do Kafka, etc.

## Executando com Docker Compose

### Produção (padrão)

```bash
docker compose up -d --build
```

Sobe PostgreSQL, Redis, Kafka, os três serviços e o `web` (nginx). **Só o nginx é publicado no host** (porta `WEB_PORT`). Banco, Redis, Kafka e os serviços ficam na rede interna do compose.

O nginx serve o SPA e roteia, na mesma origem do site (sem CORS):

| Caminho | Destino |
|---|---|
| `/auth/` | auth-service (com limite de 5 req/s por IP e rajada de 20) |
| `/ws/` | chat-service (WebSocket/STOMP) |
| `/rooms/{id}/messages` | message-service |
| `/rooms` | chat-service |
| demais | SPA (Angular) |

O script `docker/init.sql` cria os bancos `whisp_chat` e `whisp_message` (o `whisp_auth` é criado pela própria imagem do Postgres). Os `/actuator` dos serviços só expõem `health`, e só dentro da rede do compose.

### Desenvolvimento local

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

Publica infraestrutura e serviços **somente em `127.0.0.1`** (PostgreSQL 5432, Redis 6379, Kafka 9092, serviços 8081 a 8083) e desliga o `Secure` do cookie. No `.env` de desenvolvimento use, por exemplo, `PUBLIC_ORIGIN=http://localhost,http://localhost:4200` e `COOKIE_SECURE=false`.

## Executando localmente (sem Docker)

1. Suba apenas a infraestrutura:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up postgres redis kafka
   ```

2. Configure os `application.yaml` de cada serviço (ver seção de Configuração). Com o Redis protegido por senha, defina também `spring.data.redis.password`.

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

A aplicação fica disponível em http://localhost:4200 e, em modo de desenvolvimento, fala direto com os serviços em `localhost:8081` a `8083`. Em produção (build `production`, servido pelo container `web`) as URLs são relativas à origem do site. Rotas: `/login`, `/register` e `/chat` (protegida por guard de autenticação).

## Testes

```bash
mvn test                       # backend (pela raiz, para o reactor compilar o whisp-common junto)
cd whisp-web && npm test       # frontend
scripts/smoke-test.sh http://localhost:8088   # stack no ar: REST, cookies, nginx e STOMP
```

Para uma stack de desenvolvimento (`docker-compose.dev.yml`) use `CHECK_CLOSED_PORTS=false EXPECT_SECURE_COOKIE=false scripts/smoke-test.sh ...`, já que nela as portas ficam abertas em `127.0.0.1` e o cookie não é `Secure`.

## Deploy na AWS (uma instância EC2)

1. **Instância:** Amazon Linux 2023 ou Ubuntu, com **pelo menos 4 GB de RAM** (por exemplo `t3.medium`). Cada JVM roda com heap de 384 MB e o Kafka com 512 MB; com a stack em repouso o `docker stats` mostrou cerca de 1,3 GB no total. O build das imagens (Maven e Angular) usa mais memória que isso, então para builds seguidos prefira `COMPOSE_PARALLEL_LIMIT=1 docker compose build` ou adicione swap; ajuste com `JAVA_TOOL_OPTIONS` e `KAFKA_HEAP_OPTS` no `.env`. Instale Docker e o plugin Compose.
2. **Security group:** libere `80` apenas para o security group do ALB (não para `0.0.0.0/0`) e `22` somente para o seu IP. **Nunca** abra 5432, 6379, 9092 nem 8081 a 8083. Com a porta 80 aberta para a internet o site também responde em HTTP puro e o limite de requisições do nginx pode ser contornado forjando `X-Forwarded-For`.
3. **HTTPS:** o nginx do projeto só escuta em HTTP (porta 8080 no container), então o TLS precisa terminar antes dele. Use um **ALB com certificado do ACM** (listener 443, target group HTTP na porta 80, health check em `/`; o ALB suporta WebSocket) e um listener 80 que redirecione para 443. Defina `PUBLIC_ORIGIN=https://seu-dominio`, exatamente a origem que o navegador usa (outro alias do domínio recebe 403 no login), e mantenha `COOKIE_SECURE=true`. Sem HTTPS o navegador descarta o cookie `Secure` e a sessão cai a cada recarga; só em HTTP local use `COOKIE_SECURE=false`. O nginx lê o IP real do cliente em `X-Forwarded-For` quando ele vem de faixas privadas da VPC, exceto a rede do compose (`172.28.0.0/24`, fixada no `docker-compose.yml`). O STOMP usa heartbeat de 20 s, então o idle timeout padrão do ALB não derruba salas paradas.
4. **Subir:**

   ```bash
   git clone <repositório> && cd whisp
   cp .env.example .env     # preencha JWT_SECRET, senhas e PUBLIC_ORIGIN
   docker compose up -d --build
   scripts/smoke-test.sh https://seu-dominio
   ```

5. **Dados:** o PostgreSQL usa o volume `postgres_data`; agende snapshots do EBS. O Redis guarda só refresh tokens (perdê-los apenas obriga a logar de novo).

**Atenção ao atualizar uma instalação existente:**
- Os tokens emitidos antes desta versão não têm a claim `token_use` e deixam de valer: todos os usuários precisam logar de novo.
- A senha do PostgreSQL só é aplicada quando o volume é criado. Num volume antigo, troque a senha com `ALTER USER whisp PASSWORD '...'` para ela bater com o `.env`.
- O `ddl-auto: update` não aumenta colunas existentes. Num banco já criado, rode `ALTER TABLE whisp_message ALTER COLUMN content TYPE varchar(2000);` no banco `whisp_message`.

**Limitações conhecidas** (aceitáveis para treino, avalie antes de produção real): uma única instância sem alta disponibilidade; Kafka sem autenticação (isolado na rede interna); Postgres com um único usuário compartilhado entre os serviços; `ddl-auto: update` em vez de migrações; salas abertas (qualquer usuário autenticado entra em qualquer sala e lê o histórico); cadastro sem verificação de email.

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
