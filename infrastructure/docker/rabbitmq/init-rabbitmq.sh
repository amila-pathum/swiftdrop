
#!/usr/bin/env bash
set -Eeuo pipefail

echo "Starting RabbitMQ..."

rabbitmq-server &
SERVER_PID=$!

cleanup() {
    echo "Stopping RabbitMQ..."
    rabbitmqctl shutdown || true
    wait "$SERVER_PID" || true
}
trap cleanup EXIT

echo "Waiting for RabbitMQ application to become ready..."

for attempt in {1..120}; do
    if rabbitmq-diagnostics -q check_running >/dev/null 2>&1; then
        echo "RabbitMQ application is running."
        break
    fi

    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "RabbitMQ server process exited unexpectedly."
        exit 1
    fi

    if [ "$attempt" -eq 120 ]; then
        echo "RabbitMQ did not become ready within 120 seconds."
        exit 1
    fi

    sleep 1
done

echo "Initializing application user..."

if rabbitmqctl list_users -q | awk '{print $1}' | grep -Fxq "$RABBITMQ_DEFAULT_USER"; then
    rabbitmqctl change_password "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
    echo "Updated existing RabbitMQ user password."
else
    rabbitmqctl add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
    echo "Created RabbitMQ user."
fi

rabbitmqctl set_user_tags "$RABBITMQ_DEFAULT_USER" administrator

rabbitmqctl set_permissions \
    -p / \
    "$RABBITMQ_DEFAULT_USER" \
    ".*" \
    ".*" \
    ".*"

echo "RabbitMQ initialization completed."

wait "$SERVER_PID"
