#!/bin/bash
# Deploy script — API Gateway Admin Panel
#
# Manual deploy to the target server when GitHub Actions isn't available.
#
# Usage:
#   chmod +x deploy/deploy-frontend.sh
#   SSH_KEY=~/.ssh/id_rsa ./deploy/deploy-frontend.sh
#
# Or specify all params:
#   SERVER=root@212.85.17.130 SSH_KEY=~/.ssh/my_key ./deploy/deploy-frontend.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="${PROJECT_DIR}/frontend"

SERVER="${SERVER:-root@212.85.17.130}"
SSH_KEY="${SSH_KEY:-~/.ssh/id_rsa}"
DEPLOY_PATH="${DEPLOY_PATH:-/var/www/api-gateway-admin}"
NGINX_CONF="${SCRIPT_DIR}/nginx-gateway-admin.conf"
PORT="${SSH_PORT:-22}"

echo "============================================"
echo " API Gateway — Deploy Admin Panel"
echo "============================================"
echo ""
echo "Target:  ${SERVER}:${PORT}"
echo "Path:    ${DEPLOY_PATH}"
echo ""

# ---- Build ----
echo "[1/4] Building React app..."
cd "$FRONTEND_DIR"
npm ci --silent
npm run build

if [ ! -d dist ]; then
    echo "ERROR: Build failed — no dist/ directory"
    exit 1
fi
echo "[OK] Build complete: $(du -sh dist | cut -f1)"

# ---- Upload ----
echo ""
echo "[2/4] Uploading to server..."
ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" "mkdir -p ${DEPLOY_PATH}"

rsync -avz --delete \
    -e "ssh -i ${SSH_KEY} -p ${PORT}" \
    dist/ \
    "${SERVER}:${DEPLOY_PATH}/"
echo "[OK] Upload complete"

# ---- Nginx ----
echo ""
echo "[3/4] Configuring Nginx..."
scp -i "$SSH_KEY" -P "$PORT" "$NGINX_CONF" "${SERVER}:/etc/nginx/sites-available/api-gateway-admin"

ssh -i "$SSH_KEY" -p "$PORT" "$SERVER" bash <<'REMOTE_SCRIPT'
set -e
ln -sf /etc/nginx/sites-available/api-gateway-admin /etc/nginx/sites-enabled/
# Install nginx if not present
command -v nginx || (apt-get update -qq && apt-get install -y -qq nginx)
nginx -t
systemctl reload nginx || service nginx reload
echo "[OK] Nginx reloaded"
REMOTE_SCRIPT

# ---- Verify ----
echo ""
echo "[4/4] Verifying deployment..."
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://${SERVER#*@}/" || echo "000")
echo "HTTP status: ${HTTP_CODE}"
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "301" ] || [ "$HTTP_CODE" = "302" ]; then
    echo "[OK] Deployment successful!"
else
    echo "[WARN] Unexpected HTTP status: ${HTTP_CODE}"
fi

echo ""
echo "============================================"
echo " DEPLOY COMPLETE"
echo "============================================"
echo "URL: http://${SERVER#*@}/"
