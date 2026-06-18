# CLAUDE.md — API Gateway

This file provides guidance to Claude Code when working on the api-gateway project.

**Contexto cross-project:** `../CLAUDE.md` para convenções compartilhadas do monorepo.

## Visão Geral

API Gateway dinâmico com configuração de rotas em tempo de execução (banco de dados), proteção JWT por rota e filtro de headers.

## Comandos

```bash
# Build & testes
./mvnw clean test                    # Todos os testes (32 testes)
./mvnw test -Dtest=NomeDoTeste       # Teste específico
./mvnw spring-boot:run               # Executar (dev profile com H2 + seed de rotas)

# Native image (GraalVM)
./mvnw -Pnative native:compile       # Executável nativo
./mvnw -Pnative spring-boot:build-image  # Container nativo

# H2 Console
# http://localhost:8080/h2-console (URL: jdbc:h2:mem:apigateway, user: sa, senha vazia)
```

## Stack

- **Java 21, Spring Boot 4.1.0, Spring Framework 7.0**
- Spring Data JPA, Spring WebMVC, Spring Actuator
- H2 (dev) / PostgreSQL (prod)
- JJWT 0.12.6 para validação JWT
- RestClient (Spring 7.0) para proxy HTTP
- Lombok (`@Builder`, `@Slf4j`, `@RequiredArgsConstructor`)

## Estrutura

```
src/main/java/dev/pedrohfreitas/api_gateway/
├── ApiGatewayApplication.java
├── config/
│   ├── GatewayConfig.java      # RestClient.Builder + ObjectMapper beans
│   └── DevDataSeeder.java      # Rotas de exemplo (dev profile)
├── controller/
│   ├── GatewayController.java  # Catch-all /** — proxy para rotas configuradas
│   └── RouteConfigController.java  # CRUD /api/gateway/routes
├── dto/
│   ├── RouteConfigRequest.java      # Record com validação Jakarta
│   ├── RouteConfigResponse.java     # Record com from(RouteConfig)
│   └── ErrorResponse.java           # Resposta de erro padronizada
├── entity/
│   └── RouteConfig.java       # JPA entity com campos de header JSON
├── exception/
│   ├── GatewayException.java        # Base (status + message)
│   ├── RouteNotFoundException.java  # 404
│   ├── DuplicateRouteException.java # 409
│   ├── JwtValidationException.java  # 401
│   ├── ProxyException.java          # 502
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
├── filter/
│   └── GatewayJwtFilter.java  # OncePerRequestFilter — valida JWT em rotas protegidas
├── repository/
│   └── RouteConfigRepository.java
├── security/
│   └── JwtService.java        # Validação JWT com HMAC-SHA256
└── service/
    ├── RouteConfigService.java  # Cache em memória, match AntPathMatcher, CRUD
    └── GatewayProxyService.java # Proxy via RestClient, filtro de headers
```

## API de Gerenciamento

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/gateway/routes` | Listar todas as rotas |
| `GET` | `/api/gateway/routes/{id}` | Buscar rota por ID |
| `POST` | `/api/gateway/routes` | Criar nova rota |
| `PUT` | `/api/gateway/routes/{id}` | Atualizar rota |
| `DELETE` | `/api/gateway/routes/{id}` | Remover rota |
| `PATCH` | `/api/gateway/routes/{id}/toggle` | Habilitar/desabilitar rota |
| `POST` | `/api/gateway/routes/refresh-cache` | Recarregar cache |

## Formato de Configuração de Headers

### addRequestHeaders / addResponseHeaders (JSON object)
```json
{"X-Custom-Header": "value1", "X-Another": "value2"}
```

### removeRequestHeaders / removeResponseHeaders (JSON array)
```json
["X-Internal-Token", "Authorization"]
```

## Seed de Rotas (Dev)

O `DevDataSeeder` cria 3 rotas de exemplo ao iniciar no perfil `dev`:
1. `/api/public/**` → `http://localhost:8081` (sem JWT)
2. `/api/secure/**` → `http://localhost:8082` (com JWT, filtra headers)
3. `/api/products/**` → `http://localhost:8083` (apenas GET/POST)

## Fluxo de uma Requisição

1. `GatewayJwtFilter` intercepta: se a rota exige JWT, valida o token
2. `GatewayController` recebe `/**` (exceto `/api/gateway/**` e `/actuator/**`)
3. `RouteConfigService.findMatchingRoute()` busca no cache (AntPathMatcher)
4. `GatewayProxyService.proxy()`:
   - Monta URL de destino (com/sem stripPrefix)
   - Copia e filtra headers do request
   - Encaminha via RestClient
   - Copia response (status, headers filtrados, body)

## Docker Swarm Deployment

```bash
# 1. Setup inicial (uma vez por cluster)
./deploy/swarm-setup.sh

# 2. Ou passo a passo:
docker swarm init                                    # se ainda não inicializado
docker network create --driver overlay --attachable gateway-net
echo -n "senha-segura" | docker secret create api_gateway_db_password -
echo -n "apigateway" | docker secret create api_gateway_db_username -
echo -n "chave-jwt-256-bits" | docker secret create api_gateway_jwt_secret -

# 3. Deploy da stack
docker stack deploy -c docker-stack.yml api-gateway

# 4. Verificar
docker stack services api-gateway
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/gateway/routes

# 5. Logs
docker service logs api-gateway_gateway

# 6. Remover
docker stack rm api-gateway
```

### CI/CD (GitHub Actions)

O pipeline `.github/workflows/Build.yml` executa:
1. **Test** — `./mvnw test` com Java 21
2. **Build & Push** — Docker multi-arch (`linux/amd64`, `linux/arm64`) via Buildx, push para registry
3. **Deploy Staging** — Swarm deploy automático no push para `develop`
4. **Deploy Production** — Swarm deploy automático no push de tags `v*`
5. **Security Scan** — Trivy scan na imagem

### Environments & Secrets necessários

| Environment | Secrets |
|-------------|---------|
| CI/CD | `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `SWARM_STAGING_HOST`, `SWARM_STAGING_SSH_KEY`, `SWARM_PROD_HOST`, `SWARM_PROD_SSH_KEY` |
| Swarm | `api_gateway_db_password`, `api_gateway_db_username`, `api_gateway_jwt_secret` |

## Atenção: API do Spring 7.0

Este projeto usa Spring Framework 7.0 onde `HttpHeaders` **não** é mais um `MultiValueMap`. Use:
- `headers.forEach((name, values) -> ...)` para iterar
- `headers.headerNames()` / `headers.headerSet()`
- `headers.add(name, value)` / `headers.set(name, value)` / `headers.remove(name)`
