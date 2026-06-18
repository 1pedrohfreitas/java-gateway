#!/bin/bash
# Deploy script — API Gateway Backend (Java JAR)
#
# Manual deploy to the target server.
#
# Usage:
#   chmod +x deploy/deploy-backend.sh
#   SSH_KEY=~/.ssh/id_rsa ./deploy/deploy-backend.sh
#
# Or specify all params:
#   SERVER=root@212.85.17.130 SSH_KEY=~/.ssh/my_key ./deploy/deploy-backend.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

SERVER="${SERVER:-root@212.85.17.130}"
SSH_KEY="${SSH_KEY:-~/.ssh/id_rsa}"
PORT="${SSH_PORT:-22}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/api-gateway}"
SERVICE_NAME="api-gateway"

echo "============================================"
echo " API Gateway — Deploy Backend"
echo "============================================"
echo ""
echo "Target:  ${SERVER}:${PORT}"
echo "Path:    ${DEPLOY_PATH}"
echo ""

# ---- Build ----
echo "[1/5] Building backend JAR..."
cd "$PROJECT_DIR"
./mvnw clean package -DskipTests -B -q

JAR_FILE=$(ls target/api-gateway-*.jar | grep -v original | head -1)
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: Build failed — no JAR in target/"
    exit 1
fi
JAR_SIZE=$(du -sh "$JAR_FILE" | cut -f1)
echo "[OK] Build complete: $JAR_FILE ($JAR_SIZE)"

# ---- Prepare server ----
echo ""
echo "[2/5] Preparing server..."

ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" bash <<REMOTE_PREPARE
set -e

# Create app user if not exists
id -u api-gateway &>/dev/null || useradd -r -s /bin/false api-gateway

# Create directories
mkdir -p ${DEPLOY_PATH}
chown api-gateway:api-gateway ${DEPLOY_PATH}

echo "[OK] Server prepared"
REMOTE_PREPARE

# ---- Upload JAR ----
echo ""
echo "[3/5] Uploading JAR..."

rsync -avz --progress \
    -e "ssh -i ${SSH_KEY} -p ${PORT}" \
    "$JAR_FILE" \
    "${SERVER}:${DEPLOY_PATH}/api-gateway.jar"

ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" "chown api-gateway:api-gateway ${DEPLOY_PATH}/api-gateway.jar"
echo "[OK] Upload complete"

# ---- Install systemd service ----
echo ""
echo "[4/5] Configuring systemd service..."

scp -i "$SSH_KEY" -P "$PORT" \
    "${SCRIPT_DIR}/api-gateway.service" \
    "${SERVER}:/tmp/api-gateway.service"

ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" bash <<REMOTE_SERVICE
set -e
mv /tmp/api-gateway.service /etc/systemd/system/api-gateway.service
systemctl daemon-reload
systemctl enable api-gateway

# Restart service (or start if not running)
if systemctl is-active --quiet api-gateway; then
    systemctl restart api-gateway
    echo "[OK] Service restarted"
else
    systemctl start api-gateway
    echo "[OK] Service started"
fi
REMOTE_SERVICE

# ---- Verify ----
echo ""
echo "[5/5] Verifying deployment..."

# Wait for startup
sleep 5

HEALTH=$(ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" \
    "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health" 2>/dev/null || echo "000")

if [ "$HEALTH" = "200" ]; then
    echo "[OK] Backend healthy! (HTTP $HEALTH)"
else
    echo "[WARN] Health check returned HTTP $HEALTH — checking logs:"
    ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" "journalctl -u api-gateway --no-pager -n 20"
fi

SERVICE_STATUS=$(ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" \
    "systemctl is-active api-gateway" 2>/dev/null || echo "unknown")

echo ""
echo "============================================"
echo " DEPLOY COMPLETE"
echo "============================================"
echo "Service:   ${SERVICE_STATUS}"
echo "Health:    http://${SERVER#*@}:8080/actuator/health"
echo "Routes:    http://${SERVER#*@}:8080/api/gateway/routes"
echo ""
echo "Logs:      ssh ${SERVER} journalctl -u api-gateway -f"
echo "Restart:   ssh ${SERVER} systemctl restart api-gateway"
