#!/opt/homebrew/bin/bash

set -euo pipefail
set -m

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/infrastructure/scripts"
RUN_SERVICE="$SCRIPT_DIR/run-service.sh"
LOG_DIR="$ROOT_DIR/logs"
PROFILE="${DB_PROFILE:-local}"

mkdir -p "$LOG_DIR"

SERVICES=(
  identity-service
  parcel-service
  merchant-service
  courier-service
  dispatch-service
  pricing-service
  notification-service
  api-gateway
  frontend
)

declare -A PIDS=()

stop_all() {
  echo
  echo "Stopping SwiftDrop services..."

  local service pid

  for service in "${SERVICES[@]}"; do
    pid="${PIDS[$service]:-}"
    if [[ -n "$pid" ]]; then
      kill -TERM -- "-$pid" 2>/dev/null || true
    fi
  done

  sleep 3

  for service in "${SERVICES[@]}"; do
    pid="${PIDS[$service]:-}"
    if [[ -n "$pid" ]] && kill -0 -- "-$pid" 2>/dev/null; then
      kill -KILL -- "-$pid" 2>/dev/null || true
    fi
  done

  echo "SwiftDrop processes stopped."
}

trap stop_all EXIT INT TERM

echo "======================================"
echo "       SwiftDrop macOS Startup"
echo "======================================"
echo "Database profile: $PROFILE"

if [[ ! -f "$RUN_SERVICE" ]]; then
  echo "ERROR: run-service.sh not found."
  exit 1
fi

if [[ "$PROFILE" == "local" ]]; then
  echo "Starting local PostgreSQL and RabbitMQ..."
  cd "$ROOT_DIR"
  docker compose up -d --wait
elif [[ "$PROFILE" == "rds" ]]; then
  if [[ ! -f "$ROOT_DIR/.env.rds" ]]; then
    echo "ERROR: .env.rds not found."
    exit 1
  fi
else
  echo "ERROR: DB_PROFILE must be local or rds."
  exit 1
fi

BACKEND_SERVICES=(
  identity-service
  parcel-service
  merchant-service
  courier-service
  dispatch-service
  pricing-service
  notification-service
)

for service in "${BACKEND_SERVICES[@]}"; do
  echo "Starting $service..."
  (
    cd "$ROOT_DIR"
    if [[ "$PROFILE" == "local" ]]; then
      exec env -u DB_PROFILE bash "$RUN_SERVICE" "$service"
    else
      exec env DB_PROFILE="$PROFILE" bash "$RUN_SERVICE" "$service"
    fi
  ) >>"$LOG_DIR/$service.log" 2>&1 &

  PIDS["$service"]=$!
done

echo "Starting api-gateway..."
(
  cd "$ROOT_DIR"
  exec env -u DB_PROFILE bash "$RUN_SERVICE" api-gateway
) >>"$LOG_DIR/api-gateway.log" 2>&1 &
PIDS["api-gateway"]=$!

echo "Starting frontend..."
(
  cd "$ROOT_DIR/frontend"
  exec npm run dev
) >>"$LOG_DIR/frontend.log" 2>&1 &
PIDS["frontend"]=$!

echo
echo "Startup commands launched."
echo "Logs: $LOG_DIR"
echo "Press Ctrl+C to stop the launched processes."

while true; do
  sleep 10

  for service in "${SERVICES[@]}"; do
    pid="${PIDS[$service]:-}"
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      echo "WARNING: $service process stopped. Check $LOG_DIR/$service.log"
    fi
  done
done
