#!/usr/bin/env bash
# Development runtime only: no changes to other services' configuration files.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
service="${1:?Choose identity-service, parcel-service, courier-service, dispatch-service or api-gateway}"
cd "$root"
unset SPRING_PROFILES_ACTIVE
set -a
source .env
set +a
export FRONTEND_ORIGIN=http://localhost:3000
export IDENTITY_SERVICE_URL=http://localhost:8081 PARCEL_SERVICE_URL=http://localhost:8082
export COURIER_SERVICE_URL=http://localhost:8084 DISPATCH_SERVICE_URL=http://localhost:8085
export IDENTITY_SERVICE_PORT=8081 PARCEL_SERVICE_PORT=8082 COURIER_SERVICE_PORT=8084 DISPATCH_SERVICE_PORT=8085 GATEWAY_PORT=8080
# Git Bash converts exported '/' values into its installation directory when
# launching Java. Let the services use their literal default-vhost setting.
unset RABBITMQ_VHOST SPRING_RABBITMQ_VIRTUAL_HOST
case "$service" in
api-gateway) export DB_PROFILE=local ;;
identity-service|parcel-service|courier-service|dispatch-service)
    set -a
    source .env.rds
    set +a
    export DB_PROFILE=rds
    database="${service%-service}_db"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${database}?sslmode=require&connectTimeout=8&socketTimeout=20"
    export SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=-1
    export SPRING_FLYWAY_CONNECT_RETRIES=3
    if [[ "$service" == dispatch-service ]]; then
        export SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS='*:future,*:missing'
    fi
    ;;
*) echo "Unsupported demo service" >&2; exit 1 ;;
esac
exec bash infrastructure/scripts/run-service.sh "$service"
