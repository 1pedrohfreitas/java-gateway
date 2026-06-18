# CLAUDE.md — API Gateway

This file provides guidance to Claude Code when working on the api-gateway project.

**Contexto cross-project:** `../CLAUDE.md` para convenções compartilhadas do monorepo.

## Visão Geral

API Gateway dinâmico com configuração de rotas em tempo de execução (banco de dados), proteção JWT por rota e filtro de headers.

## Comandos

```bash
# Backend — build & testes
./mvnw clean test                    # Todos os testes (32 testes)
./mvnw test -Dtest=NomeDoTeste       # Teste específico
./mvnw spring-boot:run               # Executar (dev profile com H2 + seed de rotas)

# Frontend — Admin Panel
cd frontend && npm install && npm run dev   # Dev server (localhost:5173)
cd frontend && npm run build                # Build produção → dist/

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
api-gateway/
├── pom.xml                          # Maven (Spring Boot 4.1.0)
├── Dockerfile                       # Multi-stage build (Maven → JRE 21 Alpine)
├── docker-compose.yml               # Dev local (PostgreSQL + Gateway)
├── docker-stack.yml                 # Docker Swarm (secrets, replicas, rolling updates)
├── entrypoint.sh                    # Runtime: lê secrets Docker, exporta env vars
├── deploy/
│   ├── swarm-setup.sh               # Setup inicial do cluster Swarm
│   ├── deploy-frontend.sh           # Deploy manual do admin panel
│   └── nginx-gateway-admin.conf     # Nginx config para o admin panel
├── .github/workflows/
│   ├── Build.yml                    # CI/CD Backend (test → build → push → deploy Swarm)
│   └── DeployFrontend.yml           # CI/CD Frontend (build → rsync → nginx reload)
├── frontend/                        # React Admin Panel (Vite + TypeScript)
│   ├── src/
│   │   ├── api/client.ts            # Axios — CRUD de rotas
│   │   ├── pages/RouteList.tsx      # Lista com toggle, delete, busca
│   │   ├── pages/RouteForm.tsx      # Formulário create/edit com headers JSON
│   │   ├── components/Layout.tsx    # Sidebar + conteúdo
│   │   ├── components/HeaderEditor.tsx  # Editor JSON para headers
│   │   └── types/route.ts          # Tipos TypeScript
│   └── vite.config.ts
└── src/main/java/.../               # Backend Java (ver seção anterior)
```

### Frontend — Admin Panel

**Stack:** React 18, TypeScript, Vite, React Router 6, Axios, react-hot-toast

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173 (proxy /api → localhost:8080)
npm run build        # Produção → dist/
```

Rotas do painel:
- `/routes` — Lista de rotas (busca, toggle, delete)
- `/routes/new` — Criar nova rota (path, target, headers JSON, JWT)
- `/routes/:id/edit` — Editar rota existente

A variável `VITE_API_URL` define a URL base da API (default: `/api/gateway`).

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

## Deploy Direto no Servidor (`root@212.85.17.130`)

### Manual

```bash
# Backend (JAR + systemd)
SSH_KEY=~/.ssh/id_rsa ./deploy/deploy-backend.sh

# Frontend (React + nginx)
SSH_KEY=~/.ssh/id_rsa ./deploy/deploy-frontend.sh
```

### systemd Service

```bash
# Instalação inicial via script de deploy (automático)
# Ou manual:
scp deploy/api-gateway.service root@212.85.17.130:/etc/systemd/system/
ssh root@212.85.17.130 "systemctl daemon-reload && systemctl enable --now api-gateway"

# Gerenciar
ssh root@212.85.17.130 systemctl status api-gateway
ssh root@212.85.17.130 journalctl -u api-gateway -f
ssh root@212.85.17.130 systemctl restart api-gateway
```

O serviço roda como usuário `api-gateway` em `/opt/api-gateway/api-gateway.jar`.

### CI/CD — Workflow Unificado

Arquivo único: `.github/workflows/ci.yml` (Node 24, Java 21)

```
push main/develop ──┬── changes (detecta paths)
                    ├── test-backend     (src/**, pom.xml)
                    ├── deploy-backend   (JAR → systemd restart)
                    ├── deploy-frontend  (dist/ → nginx reload)
                    │
push tag v*    ────┬── test-backend
                    ├── swarm-build     (Docker buildx multi-arch)
                    ├── swarm-deploy    (stack deploy no cluster)
                    └── scan            (Trivy vulnerability scan)

workflow_dispatch ── Opções manuais:
                       ☑ deploy_backend  ☑ deploy_frontend
                       ☐ swarm_deploy    ☐ skip_tests
```

**Path detection:** o job `changes` usa `dorny/paths-filter` para detectar quais arquivos mudaram. Jobs só rodam se os paths relevantes foram alterados (ou se for `workflow_dispatch`).

### Secrets necessários no GitHub

| Secret | Uso |
|--------|-----|
| `SSH_HOST` | IP/hostname do servidor de deploy |
| `SSH_USER` | Usuário SSH (`root`) |
| `SSH_PORT` | Porta SSH (default `22`) |
| `SSH_PRIVATE_KEY` | Chave SSH privada |
| `VITE_API_URL` | URL base da API no frontend |
| `REGISTRY_USERNAME` | (Swarm) Docker registry |
| `REGISTRY_PASSWORD` | (Swarm) Docker registry |
| `SWARM_PROD_HOST` | (Swarm) Host do cluster |
| `SWARM_PROD_USER` | (Swarm) Usuário SSH |
| `SWARM_PROD_SSH_KEY` | (Swarm) Chave SSH |

## Atenção: API do Spring 7.0

Este projeto usa Spring Framework 7.0 onde `HttpHeaders` **não** é mais um `MultiValueMap`. Use:
- `headers.forEach((name, values) -> ...)` para iterar
- `headers.headerNames()` / `headers.headerSet()`
- `headers.add(name, value)` / `headers.set(name, value)` / `headers.remove(name)`
