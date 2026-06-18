#!/bin/bash
# Swarm Setup Script — api-gateway
#
# Run this ONCE per Swarm cluster to create secrets and prepare the environment.
#
# Usage:
#   chmod +x deploy/swarm-setup.sh
#   ./deploy/swarm-setup.sh
#
# Prerequisites:
#   - Docker Swarm initialized (`docker swarm init`)
#   - Docker registry accessible to all swarm nodes

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STACK_DIR="$(dirname "$SCRIPT_DIR")"
STACK_NAME="${STACK_NAME:-api-gateway}"

echo "============================================"
echo " API Gateway — Swarm Setup"
echo " Stack: ${STACK_NAME}"
echo "============================================"
echo ""

# ---- Ensure Swarm is active ----
if ! docker info --format '{{.Swarm.LocalNodeState}}' 2>/dev/null | grep -q "active"; then
    echo "ERROR: Docker Swarm is not active on this node."
    echo "Initialize it first: docker swarm init"
    exit 1
fi
echo "[OK] Docker Swarm is active"

# ---- Create overlay network (if not exists) ----
if ! docker network ls --filter name=gateway-net --format '{{.Name}}' | grep -q "gateway-net"; then
    echo "Creating overlay network: gateway-net"
    docker network create --driver overlay --attachable gateway-net
else
    echo "[OK] Overlay network 'gateway-net' already exists"
fi

# ---- Create Secrets ----
create_secret() {
    local name=$1
    local value=$2
    local description=$3

    if docker secret ls --format '{{.Name}}' | grep -q "^${name}$"; then
        echo "[SKIP] Secret '${name}' already exists"
    else
        if [ -z "$value" ]; then
            # Generate random secret
            value=$(openssl rand -base64 48)
            echo "Generated random value for '${name}'"
        fi
        echo -n "$value" | docker secret create "${name}" -
        echo "[OK] Secret '${name}' created"
    fi
}

echo ""
echo "---- Creating Secrets ----"

# DB Password (random if not provided)
create_secret "api_gateway_db_password" "${DB_PASSWORD:-}" "PostgreSQL password"
create_secret "api_gateway_db_username" "${DB_USERNAME:-apigateway}" "PostgreSQL username"

# JWT Secret (random if not provided)
if [ -z "$JWT_SECRET" ]; then
    JWT_SECRET=$(openssl rand -base64 48)
    echo ""
    echo "============================================"
    echo " GENERATED JWT SECRET (save this!):"
    echo " ${JWT_SECRET}"
    echo "============================================"
    echo ""
fi
create_secret "api_gateway_jwt_secret" "$JWT_SECRET" "JWT signing key"

# ---- Deploy the stack ----
echo ""
echo "---- Deploying Stack ----"
echo "Stack file: ${STACK_DIR}/docker-stack.yml"

docker stack deploy -c "${STACK_DIR}/docker-stack.yml" "${STACK_NAME}"

echo ""
echo "---- Waiting for services to stabilize ----"
sleep 5

# Show status
echo ""
docker stack services "${STACK_NAME}"
echo ""

# Wait for gateway to be healthy
echo "Waiting for gateway health check..."
for i in $(seq 1 30); do
    STATUS=$(docker service ls --filter name="${STACK_NAME}_gateway" --format '{{.Replicas}}' 2>/dev/null)
    if echo "$STATUS" | grep -qE '^[0-9]+/[0-9]+$'; then
        CURRENT=$(echo "$STATUS" | cut -d/ -f1)
        DESIRED=$(echo "$STATUS" | cut -d/ -f2)
        if [ "$CURRENT" = "$DESIRED" ] && [ "$DESIRED" -gt 0 ]; then
            echo "[OK] Gateway service is running ($CURRENT/$DESIRED replicas)"
            break
        fi
    fi
    sleep 5
done

echo ""
echo "============================================"
echo " DEPLOYMENT COMPLETE"
echo "============================================"
echo ""
echo "Stack name:  ${STACK_NAME}"
echo "Services:"
docker stack services "${STACK_NAME}" --format "  {{.Name}}  {{.Replicas}}  {{.Ports}}"
echo ""
echo "Test: curl http://localhost:8080/actuator/health"
echo "Routes: curl http://localhost:8080/api/gateway/routes"
echo ""
echo "Manage:"
echo "  docker stack services ${STACK_NAME}"
echo "  docker service logs ${STACK_NAME}_gateway"
echo "  docker stack rm ${STACK_NAME}"
