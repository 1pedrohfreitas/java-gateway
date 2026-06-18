#!/bin/sh
set -e

# ---- Docker Secrets Support ----
# If secrets are mounted at /run/secrets/, read them into environment variables.

# Database credentials
if [ -f /run/secrets/db_password ]; then
    export DATABASE_PASSWORD=$(cat /run/secrets/db_password)
fi
if [ -f /run/secrets/db_username ]; then
    export DATABASE_USERNAME=$(cat /run/secrets/db_username)
fi

# JWT secret
if [ -f /run/secrets/jwt_secret ]; then
    export GATEWAY_JWT_SECRET=$(cat /run/secrets/jwt_secret)
fi

# ---- Runtime Config Overrides ----
# Allow overriding Spring Boot settings via environment variables.
# These are set as Docker service environment or via configs.

# Database URL (defaults to PostgreSQL in the same swarm)
: "${DATABASE_URL:=jdbc:postgresql://postgres:5432/apigateway}"
: "${DATABASE_USERNAME:=apigateway}"
: "${DATABASE_PASSWORD:=apigateway}"

# JWT
: "${GATEWAY_JWT_SECRET:=change-me-in-production-use-at-least-256-bits-key}"

# Activate production profile
export SPRING_PROFILES_ACTIVE=prod

# Export as Spring Boot properties
export SPRING_DATASOURCE_URL="${DATABASE_URL}"
export SPRING_DATASOURCE_USERNAME="${DATABASE_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${DATABASE_PASSWORD}"
export GATEWAY_JWT_SECRET="${GATEWAY_JWT_SECRET}"

echo "Starting api-gateway..."
echo "  Profile:       ${SPRING_PROFILES_ACTIVE}"
echo "  Database URL:  ${SPRING_DATASOURCE_URL}"
echo "  Database User: ${SPRING_DATASOURCE_USERNAME}"
echo "  JWT Secret:    [${#GATEWAY_JWT_SECRET} chars]"

exec java \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Xms128m -Xmx256m \
    -jar /app/app.jar
